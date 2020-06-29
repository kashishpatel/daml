// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.speedy

/**
  Transformation to ANF based AST for the speedy interpreter.

  "ANF" stands for A-normal form.
  In essence it means that sub-expressions of most expression nodes are in atomic-form.
  The one exception is the let-expression.

  Atomic mean: a variable reference (ELoc), a (literal) value, or a builtin.
  This is captured by any speedy-expression which `extends SExprAtomic`.

  TODO: <EXAMPLE HERE>

  We reason we convert to ANF is to improve the efficiency of speedy execution: the
  execution engine can take advantage of the atomic assumption, and often removes
  additional execution steps - in particular the pushing of continuations to allow
  execution to continue after a compound expression is reduced to a value.

  The speedy machine now expects that it will never have to execute a non-ANF expression,
  crashing at runtime if one is encountered.  In particular we must ensure that the
  expression forms: SEAppGeneral and SECase are removed, and replaced by the simpler
  SEAppAtomic and SECaseAtomic (plus SELet as required).

  */
import com.daml.lf.speedy.SExpr._

import scala.annotation.tailrec

object Anf {

  /*** Entry point for the ANF transformation phase */
  def flattenToAnf(exp: SExpr): AExpr = {
    val depth = DepthA(0)
    val env = initEnv
    flattenExp(depth, env, exp, flattenedExpression => Land(flattenedExpression)).bounce
  }

  /**
    The transformation code is implemented using a continuation-passing style of
    translation (which is quite common for translation to ANF). In general, naming nested
    compound expressions requires turning an expression kind of inside out, lifting the
    introduced let-expression up to the the nearest enclosing abstraction or case-branch.

    For speedy, the ANF pass occurs after translation to De-Bruin and closure conversions,
    which adds the additional complication of re-indexing the variable indexes. This is
    achieved by tracking the old and new depth & the mapping between them. See the types:
    DepthE, DepthA and Env.

    There is also the issue of avoiding stack-overflow during compilation, which is
    managed by the using of a Trampoline[T] type.
    */
  case class CompilationError(error: String) extends RuntimeException(error)

  /** `DepthE` tracks the stack-depth of the original expression being traversed */
  case class DepthE(n: Int)

  /** `DepthA` tracks the stack-depth of the ANF expression being constructed */
  case class DepthA(n: Int)

  /** `Env` contains the mapping from old to new depth, as well as the old-depth as these
    * components always travel together */
  case class Env(absMap: Map[DepthE, DepthA], oldDepth: DepthE)

  val initEnv = Env(absMap = Map.empty, oldDepth = DepthE(0))

  def trackBindings(depth: DepthA, env: Env, n: Int): Env = {
    if (n == 0) {
      env
    } else {
      val extra = (0 to n - 1).map(i => (DepthE(env.oldDepth.n + i), DepthA(depth.n + i)))
      Env(absMap = env.absMap ++ extra, oldDepth = DepthE(env.oldDepth.n + n))
    }
  }

  // TODO: reference something here about trampolines
  sealed abstract class Trampoline[T] {
    @tailrec
    final def bounce: T = this match {
      case Land(x) => x
      case Bounce(continue) => continue().bounce
    }
  }

  final case class Land[T](x: T) extends Trampoline[T]
  final case class Bounce[T](continue: () => Trampoline[T]) extends Trampoline[T]

  /** `K[T]` is the continuation type which must be passed to the core transformation
    functions, i,e, `transformExp`.

    Notice how the DepthA is threaded through the continuation.
    */
  type K[T] = ((DepthA, T) => AExpr)

  /** During conversion we need to deal with bindings which are made/found at a given
    absolute stack depth. These are represented using `AbsBinding`.

    Note the contrast with the expression form `ELocS` which indicates a relative offset
    from the top of the stack. This relative-position is used in both the original
    expression which we traverse AND the new ANF expression we are constructing.
    */
  case class AbsBinding(abs: DepthA)

  def makeAbsoluteB(env: Env, rel: Int): AbsBinding = {
    val oldAbs = DepthE(env.oldDepth.n - rel)
    env.absMap.get(oldAbs) match {
      case None => throw CompilationError(s"makeAbsoluteB(env=$env,rel=$rel)")
      case Some(abs) => AbsBinding(abs)
    }
  }

  def makeRelativeB(depth: DepthA, binding: AbsBinding): Int = {
    (depth.n - binding.abs.n)
  }

  type AbsAtom = Either[SExprAtomic, AbsBinding]

  def makeAbsoluteA(env: Env, atom: SExprAtomic): AbsAtom = atom match {
    case SELocS(rel) => Right(makeAbsoluteB(env, rel))
    case x => Left(x)
  }

  def makeRelativeA(depth: DepthA)(atom: AbsAtom): SExprAtomic = atom match {
    case Left(x: SELocS) => throw CompilationError(s"makeRelativeA: unexpected: $x")
    case Left(atom) => atom
    case Right(binding) => SELocS(makeRelativeB(depth, binding))
  }

  def relocateA(depth: DepthA, env: Env)(atom: SExprAtomic): SExprAtomic = {
    makeRelativeA(depth)(makeAbsoluteA(env, atom))
  }

  type AbsLoc = Either[SELoc, AbsBinding]

  def makeAbsoluteL(env: Env, loc: SELoc): AbsLoc = loc match {
    case SELocS(rel) => Right(makeAbsoluteB(env, rel))
    case x: SELocA => Left(x)
    case x: SELocF => Left(x)
  }

  def makeRelativeL(depth: DepthA)(loc: AbsLoc): SELoc = loc match {
    case Left(x: SELocS) => throw CompilationError(s"makeRelativeL: unexpected: $x")
    case Left(loc) => loc
    case Right(binding) => SELocS(makeRelativeB(depth, binding))
  }

  def relocateL(depth: DepthA, env: Env)(loc: SELoc): SELoc = {
    makeRelativeL(depth)(makeAbsoluteL(env, loc))
  }

  def flattenExp[A](depth: DepthA, env: Env, exp: SExpr, k: (AExpr => A)): A = {
    k(transformExp(depth, env, exp, { case (_, sexpr) => Land(AExpr(sexpr)) }).bounce)
  }

  def transformLet1(depth: DepthA, env: Env, rhs: SExpr, body: SExpr, k: (DepthA, SExpr) => Trampoline[AExpr]): Trampoline[AExpr] = {
    transformExp(depth, env, rhs, {
      case (depth, rhs) =>
        val depth1 = DepthA(depth.n + 1)
        val env1 = trackBindings(depth, env, 1)
        val body1 = Bounce(() => transformExp(depth1, env1, body, k))
        Bounce(() => Land(AExpr(SELet1(rhs, body1.bounce.wrapped))))
    })
  }

  def flattenAlts(depth: DepthA, env: Env, alts: Array[SCaseAlt]): Array[SCaseAlt] = {
    alts.map {
      case SCaseAlt(pat, body0) =>
        val n = patternNArgs(pat)
        val env1 = trackBindings(depth, env, n)
        flattenExp(DepthA(depth.n + n), env1, body0, body => {
          SCaseAlt(pat, body.wrapped)
        })
    }
  }

  def patternNArgs(pat: SCasePat): Int = pat match {
    case _: SCPEnum | _: SCPPrimCon | SCPNil | SCPDefault | SCPNone => 0
    case _: SCPVariant | SCPSome => 1
    case SCPCons => 2
  }

  /** `transformExp` is the function at the heart of the ANF transformation.  You can read
    it's type as saying: "Caller, give me a general expression `exp`, (& depth/env info),
    and a continuation function `k` which says what you want to do with the transformed
    expression. Then I will do the transform, and call `k` with it. I reserve the right to
    wrap further expression-AST around the expression returned by `k`.
    See: `atomizeExp` for a instance where this wrapping occurs.
    */
  def transformExp(depth: DepthA, env: Env, exp: SExpr, k: (DepthA, SExpr) => Trampoline[AExpr]): Trampoline[AExpr] =
      exp match {
        case atom: SExprAtomic => Bounce(() => k(depth, relocateA(depth, env)(atom)))
        case x: SEVal => Bounce(() => k(depth, x))
        case x: SEImportValue => Bounce(() => k(depth, x))

        // (NC) I'm not entirely happy with how the following code is formatted, but
        // scalafmt wont have it any other way.
        case SEAppGeneral(func, args) =>
          Bounce(() => atomizeExp(
            depth,
            env,
            func, {
              case (depth, func, transk) =>
                transk(atomizeExps(
                  depth,
                  env,
                  args.toList, {
                    case (depth, args) =>
                      val func1 = makeRelativeA(depth)(func)
                      val args1 = args.map(makeRelativeA(depth))
                      Bounce(() => k(depth, SEAppAtomic(func1, args1.toArray)))
                  }
                ))
            }
          ))
        case SEMakeClo(fvs0, arity, body0) =>
          val fvs = fvs0.map(relocateL(depth, env))
          val body = flattenToAnf(body0).wrapped
          Bounce(() => k(depth, SEMakeClo(fvs, arity, body)))

        case SECase(scrut, alts0) =>
          Bounce(() => atomizeExp(depth, env, scrut, {
            case (depth, scrut, transk) =>
              val scrut1 = makeRelativeA(depth)(scrut)
              val alts = flattenAlts(depth, env, alts0)
              Bounce(() => transk(k(depth, SECaseAtomic(scrut1, alts))))
          }))

        case SELet(rhss, body) =>
          val expanded = expandMultiLet(rhss.toList, body)
          Bounce(() => transformExp(depth, env, expanded, k))

        case SELet1General(rhs, body) =>
          Bounce(() => transformLet1(depth, env, rhs, body, k))

        case SECatch(body0, handler0, fin0) =>
          Bounce(() => flattenExp(depth, env, body0, body => {
            Bounce(() => flattenExp(depth, env, handler0, handler => {
              Bounce(() => flattenExp(depth, env, fin0, fin => {
                Bounce(() => k(depth, SECatch(body.wrapped, handler.wrapped, fin.wrapped)))
              }))
            }))
          }))

        case SELocation(loc, body) =>
          Bounce(() => transformExp(depth, env, body, {
            case (depth, body) =>
              Bounce(() => k(depth, SELocation(loc, body)))
          }))

        case SELabelClosure(label, exp) =>
          Bounce(() => transformExp(depth, env, exp, {
            case (depth, exp) =>
              Bounce(() => k(depth, SELabelClosure(label, exp)))
          }))

        case x: SEAbs => throw CompilationError(s"flatten: unexpected: $x")
        case x: SEWronglyTypeContractId => throw CompilationError(s"flatten: unexpected: $x")
        case x: SEVar => throw CompilationError(s"flatten: unexpected: $x")

        case x: SEAppAtomicGeneral => throw CompilationError(s"flatten: unexpected: $x")
        case x: SEAppAtomicSaturatedBuiltin => throw CompilationError(s"flatten: unexpected: $x")
        case x: SELet1Builtin => throw CompilationError(s"flatten: unexpected: $x")
        case x: SECaseAtomic => throw CompilationError(s"flatten: unexpected: $x")

    }

  def atomizeExps(depth: DepthA, env: Env, exps: List[SExpr], k: (DepthA, List[AbsAtom]) => Trampoline[AExpr]): Trampoline[AExpr] =
    exps match {
      case Nil => k(depth, Nil)
      case exp :: exps =>
          Bounce(() => atomizeExp(depth, env, exp, {
            case (depth, atom, transk) =>
              transk(atomizeExps(depth, env, exps, {
                case (depth, atoms) =>
                  k(depth, atom :: atoms)
              }))
          }))
    }

  def atomizeExp(depth: DepthA, env: Env, exp: SExpr, transform: (DepthA, AbsAtom, Trampoline[AExpr] => Trampoline[AExpr]) => Trampoline[AExpr]): Trampoline[AExpr] = {
    exp match {
      case ea: SExprAtomic => Bounce(() => transform(depth, makeAbsoluteA(env, ea), ae => ae))
      case _ =>
        Bounce(() => transformExp(
          depth,
          env,
          exp, {
            case (depth, anf) =>
              val atom = Right(AbsBinding(depth))
              // wrap the transformed body with an enclosing let expression using a newly introduced variable
              Bounce(() => transform(DepthA(depth.n + 1), atom, body => Bounce(() => Land(AExpr(SELet1(anf, body.bounce.wrapped))))))
          }
        ))
    }
  }

  def expandMultiLet(rhss: List[SExpr], body: SExpr): SExpr = {
    //loop over rhss in reverse order
    @tailrec
    def loop(acc: SExpr, xs: List[SExpr]): SExpr = {
      xs match {
        case Nil => acc
        case rhs :: xs => loop(SELet1General(rhs, acc), xs)
      }
    }
    loop(body, rhss.reverse)
  }

}
