// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.lf.transaction

import com.daml.lf.data.Ref.Party
import com.daml.lf.data.Relation.Relation
import com.daml.lf.value.Value.ContractId

/** This gives disclosure and divulgence info.
  *
  * "Disclosure" tells us which nodes to communicate to which parties.
  * See also https://docs.daml.com/concepts/ledger-model/ledger-privacy.html
  *
  * "Divulgence" tells us what to communicate to
  * each participant node so that they can perform post-commit
  * validation. Note that divulgence can also divulge
  * contract ids -- e.g. contract ids that were created
  * _outside_ this transaction.
  * See also https://docs.daml.com/concepts/ledger-model/ledger-privacy.html#divulgence-when-non-stakeholders-see-contracts
  */
final case class BlindingInfo(
    /** Disclosure, specified in terms of local node IDs */
    disclosure: Relation[Transaction.NodeId, Party],
    /**
      * Divulgence, specified in terms of contract IDs.
      * Note that if this info was produced by blinding a transaction
      * containing only contract ids, this map may also
      * contain contracts produced in the same transaction.
      */
    divulgence: Relation[ContractId, Party],
)
