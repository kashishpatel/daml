.. Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
.. SPDX-License-Identifier: Apache-2.0

.. _sandbox-manual:

DAML Sandbox
############

The DAML Sandbox, or Sandbox for short, is a simple ledger implementation that enables rapid application prototyping by simulating a DAML Ledger.

You can start Sandbox together with :doc:`Navigator </tools/navigator/index>` using the ``daml start`` command in a DAML SDK project. This command will compile the DAML file and its dependencies as specified in the ``daml.yaml``. It will then launch Sandbox passing the just obtained DAR packages. Sandbox will also be given the name of the startup scenario specified in the project's ``daml.yaml``. Finally, it launches the navigator connecting it to the running Sandbox.

It is possible to execute the Sandbox launching step in isolation by typing ``daml sandbox``.

Note: Sandbox has switched to use Wall Clock Time mode by default. To use Static Time Mode you can provide the ``--static-time`` flag to the ``daml sandbox`` command or configure the time mode for ``daml start`` in ``sandbox-options:`` section of ``daml.yaml``. Please refer to :ref:`DAML configuration files <daml-yaml-configuration>` for more information.

Sandbox can also be run manually as in this example:

.. code-block:: none

  $ daml sandbox Main.dar --static-time --scenario Main:example

     ____             ____
    / __/__ ____  ___/ / /  ___ __ __
   _\ \/ _ `/ _ \/ _  / _ \/ _ \\ \ /
  /___/\_,_/_//_/\_,_/_.__/\___/_\_\
  initialized sandbox with ledger-id = sandbox-16ae201c-b2fd-45e0-af04-c61abe13fed7, port = 6865,
  dar file = DAR files at List(/Users/damluser/temp/da-sdk/test/Main.dar), time mode = Static, daml-engine = {}
  Initialized Static time provider, starting from 1970-01-01T00:00:00Z
  listening on localhost:6865

Here, ``daml sandbox`` tells the SDK Assistant to run ``sandbox`` from the active SDK release and pass it any arguments that follow. The example passes the DAR file to load (``Main.dar``) and the optional ``--scenario`` flag tells Sandbox to run the ``Main:example`` scenario on startup. The scenario must be fully qualified; here ``Main`` is the module and ``example`` is the name of the scenario, separated by a ``:``. We also specify that the Sandbox should run in Static Time mode so that the scenario can control the time.

.. note::

  The scenario is used for testing and development only, and is not supported by production DAML Ledgers. It is therefore inadvisable to rely on scenarios for ledger initialization.

  ``submitMustFail`` is only supported by the test-ledger used by ``daml test`` and the IDE, not by the Sandbox.

Contract Identifier Generation
******************************

Sandbox supports two contract identifier generator schemes:

- The so-called *deterministic* scheme that deterministically produces
  contract identifiers from the state of the underlying ledger.  Those
  identifiers are strings starting with ``#``.

- The so-called *random* scheme that produces contract identifiers
  indistinguishable from random.  In practice, the schemes use a
  cryptographically secure pseudorandom number generator initialized
  with a truly random seed. Those identifiers are hexadecimal strings
  prefixed by ``00``.

The sandbox can be configured to use one or the other scheme with one
of the following command line options:

- ``--contract-id-seeding=<seeding-mode>``.  The Sandbox will use the
  seeding mode `<seeding-mode>` to seed the generation of random
  contract identifiers. Possible seeding modes are:

  - ``no``: The Sandbox uses the ``deterministic`` scheme.

  - ``strong``: The Sandbox uses the ``random`` scheme initialized
    with a high-entropy seed.  Depending on the underlying operating
    system, the startup of the Sandbox may block as entropy is being
    gathered to generate the seed.

  - ``testing-weak``: (**For testing purposes only**) The Sandbox uses
    the ``random`` scheme initialized with a low entropy seed.  This
    may be used in a testing environment to avoid exhausting the
    system entropy pool when a large number of Sandboxes are started
    in a short time interval.

  - ``testing-static``: (**For testing purposes only**) The sandbox
    uses the ``random`` scheme with a fixed seed. This may be used in
    testing for reproducible runs.


Running with persistence
************************

By default, Sandbox uses an in-memory store, which means it loses its state when stopped or restarted. If you want to keep the state, you can use a Postgres database for persistence. This allows you to shut down Sandbox and start it up later, continuing where it left off.

To set this up, you must:

- create an initially empty Postgres database that the Sandbox application can access
- have a database user for Sandbox that has authority to execute DDL operations

  This is because Sandbox manages its own database schema, applying migrations if necessary when upgrading versions.

To start Sandbox using persistence, pass an ``--sql-backend-jdbcurl <value>`` option, where ``<value>`` is a valid jdbc url containing the username, password and database name to connect to.

Here is an example for such a url: ``jdbc:postgresql://localhost/test?user=fred&password=secret``

Due to possible conflicts between the ``&`` character and various terminal shells, we recommend quoting the jdbc url like so:

.. code-block:: none

  $ daml sandbox Main.dar --sql-backend-jdbcurl "jdbc:postgresql://localhost/test?user=fred&password=secret"

If you're not familiar with JDBC URLs, see the JDBC docs for more information: https://jdbc.postgresql.org/documentation/head/connect.html

.. _sandbox-authentication:

Running with authentication
***************************

By default, Sandbox does not use any authentication and accepts all valid ledger API requests.

To start Sandbox with authentication based on `JWT <https://jwt.io/>`__ tokens,
use one of the following command line options:

- ``--auth-jwt-rs256-crt=<filename>``.
  The sandbox will expect all tokens to be signed with RS256 (RSA Signature with SHA-256) with the public key loaded from the given X.509 certificate file.
  Both PEM-encoded certificates (text files starting with ``-----BEGIN CERTIFICATE-----``)
  and DER-encoded certificates (binary files) are supported.

- ``--auth-jwt-es256-crt=<filename>``.
  The sandbox will expect all tokens to be signed with ES256 (ECDSA using P-256 and SHA-256) with the public key loaded from the given X.509 certificate file.
  Both PEM-encoded certificates (text files starting with ``-----BEGIN CERTIFICATE-----``)
  and DER-encoded certicates (binary files) are supported.

- ``--auth-jwt-es512-crt=<filename>``.
  The sandbox will expect all tokens to be signed with ES512 (ECDSA using P-521 and SHA-512)     with the public key loaded from the given X.509 certificate file.
  Both PEM-encoded certificates (text files starting with ``-----BEGIN CERTIFICATE-----``)
  and DER-encoded certificates (binary files) are supported.

- ``--auth-jwt-rs256-jwks=<url>``.
  The sandbox will expect all tokens to be signed with RS256 (RSA Signature with SHA-256) with the public key loaded from the given `JWKS <https://tools.ietf.org/html/rfc7517>`__ URL.

.. warning::

  For testing purposes only, the following options may also be used.
  None of them is considered safe for production:

  - ``--auth-jwt-hs256-unsafe=<secret>``.
    The sandbox will expect all tokens to be signed with HMAC256 with the given plaintext secret.

Token payload
=============

JWTs express claims which are documented in the :ref:`authentication <authentication-claims>` documentation.

The following is an example of a valid JWT payload:

.. code-block:: json

   {
      "https://daml.com/ledger-api": {
        "ledgerId": "aaaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        "participantId": null,
        "applicationId": null,
        "admin": true,
        "actAs": ["Alice"],
        "readAs": ["Bob"]
      },
      "exp": 1300819380
   }

where

- ``ledgerId``, ``participantId``, ``applicationId`` restricts the validity of the token to the given ledger, participant, or application
- ``exp`` is the standard JWT expiration date (in seconds since EPOCH)
- ``admin``, ``actAs`` and ``readAs`` bear the same meaning as in the :ref:`authentication <authentication-claims>` documentation

The ``public`` claim is implicitly held by anyone bearing a valid JWT (even without being an admin or being able to act or read on behalf of any party).

Generating JSON Web Tokens (JWT)
================================

To generate tokens for testing purposes, use the `jwt.io <https://jwt.io/>`__ web site.


Generating RSA keys
===================

To generate RSA keys for testing purposes, use the following command

.. code-block:: none

  openssl req -nodes -new -x509 -keyout sandbox.key -out sandbox.crt

which generates the following files:

- ``sandbox.key``: the private key in PEM/DER/PKCS#1 format
- ``sandbox.crt``: a self-signed certificate containing the public key, in PEM/DER/X.509 Certificate format

Generating EC keys
==================

To generate keys to be used with ES256 for testing purposes, use the following command

.. code-block:: none

  openssl req -x509 -nodes -days 3650 -newkey ec:<(openssl ecparam -name prime256v1) -keyout ecdsa256.key -out ecdsa256.crt

which generates the following files:

- ``ecdsa256.key``: the private key in PEM/DER/PKCS#1 format
- ``ecdsa256.crt``: a self-signed certificate containing the public key, in PEM/DER/X.509 Certificate format

Similarly, you can use the following command for ES512 keys:

.. code-block:: none

  openssl req -x509 -nodes -days 3650 -newkey ec:<(openssl ecparam -name secp521r1) -keyout ecdsa512.key -out ecdsa512.crt

.. _sandbox-tls:

Running with TLS
****************

To enable TLS, you need to specify the private key for your server and
the certificate chain via ``daml sandbox --pem server.pem --crt
server.crt``.  By default, Sandbox requires client authentication as
well. You can set a custom root CA certificate used to validate client
certificates via ``--cacrt ca.crt``. You can change the client
authentication mode via ``--client-auth none`` which will disable it
completely, ``--client-auth optional`` which makes it optional or
specify the default explicitly via ``-.client-auth require``.

Command-line reference
**********************

To start Sandbox, run: ``sandbox [options] <archive>...``.

To see all the available options, run ``daml sandbox --help``.

Metrics
*******

Enable and configure reporting
==============================

To enable metrics and configure reporting, you can use the two following CLI options:

- ``--metrics-reporter``: passing a legal value will enable reporting; the accepted values
  are ``console``, ``csv:</path/to/metrics.csv>`` and ``graphite:<local_server_port>``.
  - ``console``: prints captured metrics on the standard output
  - ``csv:</path/to/metrics.csv>``: saves the captured metrics in CSV format at the specified location
  - ``graphite:<local_server_port>``: sends captured metrics to a local Graphite server. If the port
    is omitted, the default value ``2003`` will be used.
- ``--metrics-reporting-interval``: metrics are pre-aggregated on the sandbox and sent to
  the reporter, this option allows the user to set the interval. The formats accepted are based
  on the ISO-8601 duration format ``PnDTnHnMn.nS`` with days considered to be exactly 24 hours.
  The default interval is 10 seconds.

Types of metrics
================

This is a list of type of metrics with all data points recorded for each.
Use this as a reference when reading the list of metrics.

Gauge
-----

An individual instantaneous measurement.

Counter
-------

Number of occurrences of some event.

Meter
-----

A meter tracks the number of times a given event occurred. The following data
points are kept and reported by any meter.

- ``<metric.qualified.name>.count``: number of registered data points overall
- ``<metric.qualified.name>.m1_rate``: number of registered data points per minute
- ``<metric.qualified.name>.m5_rate``: number of registered data points every 5 minutes
- ``<metric.qualified.name>.m15_rate``: number of registered data points every 15 minutes
- ``<metric.qualified.name>.mean_rate``: mean number of registered data points

Histogram
---------

An histogram records aggregated statistics about collections of events.
The exact meaning of the number depends on the metric (e.g. timers
are histograms about the time necessary to complete an operation).

- ``<metric.qualified.name>.mean``: arithmetic mean
- ``<metric.qualified.name>.stddev``: standard deviation
- ``<metric.qualified.name>.p50``: median
- ``<metric.qualified.name>.p75``: 75th percentile
- ``<metric.qualified.name>.p95``: 95th percentile
- ``<metric.qualified.name>.p98``: 98th percentile
- ``<metric.qualified.name>.p99``: 99th percentile
- ``<metric.qualified.name>.p999``: 99.9th percentile
- ``<metric.qualified.name>.min``: lowest registered value overall
- ``<metric.qualified.name>.max``: highest registered value overall

Histograms only keep a small *reservoir* of statistically relevant data points
to ensure that metrics collection can be reasonably accurate without being
too taxing resource-wise.

Unless mentioned otherwise all histograms (including timers, mentioned below)
use exponentially decaying reservoirs (i.e. the data is roughly relevant for
the last five minutes of recording) to ensure that recent and possibly
operationally relevant changes are visible through the metrics reporter.

Note that ``min`` and ``max`` values are not affected by the reservoir sampling policy.

You can read more about reservoir sampling and possible associated policies
in the `Dropwizard Metrics library documentation <https://metrics.dropwizard.io/4.1.2/manual/core.html#man-core-histograms/>`__.

Timers
------

A timer records all metrics registered by a meter and by an histogram, where
the histogram records the time necessary to execute a given operation (measured
in milliseconds, unless otherwise specified).

Cache Metrics
-------------

A "cache metric" is a collection of simpler metrics that keep track of
relevant numbers about caches kept by the system to avoid re-running
expensive operations.

The metrics are:

- ``<metric.qualified.name>.size`` (gauge): instant measurement of the number of cached items
- ``<metric.qualified.name>.weight`` (gauge): instant measurement of the number of the (possibly approximate) size in bytes of cached items
- ``<metric.qualified.name>.hitCount`` (counter): how many times the cache was successfully accessed to retrieve an item
- ``<metric.qualified.name>.missCount`` (counter): how many times the cache did not have the required item and had to load it
- ``<metric.qualified.name>.loadSuccessCount`` (counter): how many times the cache successfully loaded an item so that it could be later served
- ``<metric.qualified.name>.loadFailureCount`` (counter): how many times the cache failed while trying to load an item
- ``<metric.qualified.name>.totalLoadTime`` (timer): overall time spent accessing the resource cached by this entity
- ``<metric.qualified.name>.evictionCount`` (counter): how many items have been evicted overall
- ``<metric.qualified.name>.evictionWeight`` (counter): (possibly approximate) size in bytes of overall evicted items

Database Metrics
----------------

A "database metric" is a collection of simpler metrics that keep track of
relevant numbers when interacting with a persistent relational store.

These metrics are:

- ``<metric.qualified.name>.wait`` (timer): time to acquire a connection to the database
- ``<metric.qualified.name>.exec`` (timer): time to run the query and read the result
  - ``<metric.qualified.name>.query`` (timer): time to run the query
  - ``<metric.qualified.name>.commit`` (timer): time to perform the commit
- ``<metric.qualified.name>.translation`` (timer): if relevant, time necessary to turn serialized DAML-LF values into in-memory objects

List of metrics
===============

How to read this list
---------------------

This list contains all the metrics recorded by a running sandbox.

Based on your setup, the list of metrics may vary. For simplicity, this list
is split in three main sections: metrics kept by any sandbox, additional metrics
when running a persistent sandbox (using the ``--sql-backend-jdbcurl`` CLI
option) and further additional metrics when running ``daml sandbox`` (as
opposed to ``daml sandbox-classic``).

Indentation is meaningful: when a metric appears indented within the list
it means that it represents a subset of the metric relative to which it's
indented; the meaning of subset varies depending on the metric: an
indented timer means that the recorded time is a span of time within
another, if it's a counter it's a quantity of a more specific set of
items.

Metrics exposed by any ``sandbox``
----------------------------------

Namespace: ``jvm``
^^^^^^^^^^^^^^^^^^

The sandbox keeps track of metrics regarding the JVM it's running on.

- ``jvm.class_loader``
- ``jvm.garbage_collector``
- ``jvm.attributes``
- ``jvm.memory_usage``
- ``jvm.thread_states``

Namespace: ``daml.commands``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the submission of commands to the DAML interpreter, along with their pre-processing.

- ``daml.commands.submissions`` (timer): time to execute a command submission on the ``CommandSubmissionService`` (directly or indirectly via the ``CommandService``)
  - ``daml.commands.validation`` (timer): time to validate a command submission
- ``daml.commands.failed_command_interpretations`` (meter): how many valid commands fail at interpretation
- ``daml.commands.deduplicated_commands`` (meter): how many commands were not executed due to deduplication
- ``daml.commands.delayed_submissions`` (meter): how many commands are delayed due to having a ledger time greater than the wall-clock time plus a skew interval
- ``daml.commands.valid_submissions`` (meter): how many submissions have been correctly interpreted

Namespace: ``daml.execution``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the execution of DAML code by the interpreter.

- ``daml.execution.lookup_active_contract`` (timer): time for the interpreter to look up each active contract that needs to be fetched to interpret a command
- ``daml.execution.lookup_active_contract_Per_Execution`` (timer): time for the interpreter to look up all active contracts that need to be fetched to interpret a command
- ``daml.execution.lookup_active_contract_Count_Per_Execution`` (histogram): statistics about the number of active contracts looked up for each command interpretation
- ``daml.execution.lookup_contract_key`` (timer): time for the interpreter to resolve each key necessary to interpret a command
- ``daml.execution.lookup_contract_key_per_execution`` (timer): time for the interpreter to resolve all keys necessary to interpret a command
- ``daml.execution.lookup_contract_key_count_per_execution`` (histogram): statistics about the number of keys resolved for each command interpretation
- ``daml.execution.get_lf_package`` (timer): time for the interpreter to fetch a compiled DAML package
- ``daml.execution.retry`` (meter): number of retries that occurred to to time skew
- ``daml.execution.total`` (timer): time to interpret a command and create a transaction

Namespace: ``daml.lapi``
^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the use of the Ledger API gRPC endpoints.

All the metrics in this namespace are timers, each tracks the time it takes to serve a request for the gRPC service with the same name (notice the different casing convention).

Endpoints that return a single response record the full time to serve it. Endpoints that return a stream record the time to return the first item in the stream.

- ``daml.lapi.active_contracts_service.get_active_contracts`` (timer)
- ``daml.lapi.command_completion_service.completion_end`` (timer)
- ``daml.lapi.command_completion_service.completion_stream`` (timer)
- ``daml.lapi.command_service.submit_and_wait`` (timer)
- ``daml.lapi.command_service.submit_and_wait_for_transaction`` (timer)
- ``daml.lapi.command_service.submit_and_wait_for_transaction_id`` (timer)
- ``daml.lapi.command_service.submit_and_wait_for_transaction_tree`` (timer)
- ``daml.lapi.command_submission_service.submit`` (timer)
- ``daml.lapi.ledger_identity_service.get_ledger_identity`` (timer)
- ``daml.lapi.package_service.get_package`` (timer)
- ``daml.lapi.package_service.list_packages`` (timer)
- ``daml.lapi.package_management_service.list_known_packages`` (timer)
- ``daml.lapi.package_management_service,`` (timer)
- ``daml.lapi.party_management_service.allocate_party`` (timer)
- ``daml.lapi.party_management_service.list_known_parties`` (timer)
- ``daml.lapi.transaction_service.ledger_end`` (timer)
- ``daml.lapi.transaction_service.get_transaction_trees`` (timer)
- ``daml.lapi.transaction_service.get_transaction_by_event_id`` (timer)
- ``daml.lapi.transaction_service.get_transaction_by_id`` (timer)
- ``daml.lapi.transaction_service.get_transaction_tree_by_event_id`` (timer)
- ``daml.lapi.transaction_service.get_transaction_tree_by_id`` (timer)
- ``daml.lapi.transaction_service.get_transactions`` (timer)

Namespace: ``daml.services.index``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the use of the controllers dispatching API requests to the underlying storage.

- ``daml.services.index.list_lf_packages`` (timer):
- ``daml.services.index.get_lf_archive`` (timer):
- ``daml.services.index.get_lf_package`` (timer):
- ``daml.services.index.package_entries`` (timer):
- ``daml.services.index.get_ledger_configuration`` (timer):
- ``daml.services.index.current_ledger_end`` (timer):
- ``daml.services.index.get_completions`` (timer):
- ``daml.services.index.transactions`` (timer):
- ``daml.services.index.transaction_trees`` (timer):
- ``daml.services.index.get_transaction_by_id`` (timer):
- ``daml.services.index.get_transaction_tree_by_id`` (timer):
- ``daml.services.index.get_active_contracts`` (timer):
- ``daml.services.index.lookup_active_contract`` (timer):
- ``daml.services.index.lookup_contract_key`` (timer):
- ``daml.services.index.lookup_maximum_ledger_time`` (timer):
- ``daml.services.index.get_ledger_id`` (timer):
- ``daml.services.index.get_participant_id`` (timer):
- ``daml.services.index.get_parties`` (timer):
- ``daml.services.index.list_known_parties`` (timer):
- ``daml.services.index.party_entries`` (timer):
- ``daml.services.index.lookup_configuration`` (timer):
- ``daml.services.index.configuration_entries`` (timer):
- ``daml.services.index.deduplicate_command`` (timer):
- ``daml.services.index.stop_deduplicating_command`` (timer):

Namespace: ``daml.services.read``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the use of the controllers dispatching API requests to the underlying storage.

- ``daml.services.read.get_ledger_initial_conditions`` (timer):
- ``daml.services.read.state_updates`` (timer):

Namespace: ``daml.services.write``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the use of the controllers dispatching API requests to the underlying storage.

- ``daml.services.write.submit_transaction`` (timer):
- ``daml.services.write.upload_packages`` (timer):
- ``daml.services.write.allocate_party`` (timer):
- ``daml.services.write.submit_configuration`` (timer):

Additional metrics exposed by a persistent ``sandbox``
------------------------------------------------------

Namespace: ``daml.index.db``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The metrics in this namespace track the use of the controllers dispatching API requests to the underlying storage
and the persistence of executed transactions on the read-optimized index that the Ledger API uses to serve requests.

- ``daml.index.db.all`` (timer): an aggregation of data regarding all queries
- ``daml.index.db.deduplicate_command`` (timer):
  - ``daml.index.db.deduplicate_command`` (database):
- ``daml.index.db.get_acs_event_sequential_id_range`` (database):
- ``daml.index.db.get_active_contracts`` (database):
- ``daml.index.db.get_completions`` (database):
- ``daml.index.db.get_event_sequential_id_range`` (database):
- ``daml.index.db.get_flat_transactions`` (database):
- ``daml.index.db.get_initial_ledger_end`` (database):
- ``daml.index.db.get_ledger_end`` (database):
- ``daml.index.db.get_ledger_id`` (database):
- ``daml.index.db.get_lf_archive`` (timer):
- ``daml.index.db.get_parties`` (timer):
- ``daml.index.db.get_transaction_trees`` (database):
- ``daml.index.db.initialize_ledger_parameters`` (database):
- ``daml.index.db.list_known_parties`` (timer):
- ``daml.index.db.list_lf_packages`` (timer):
- ``daml.index.db.load_all_parties`` (database):
- ``daml.index.db.load_archive`` (database):
- ``daml.index.db.load_configuration_entries`` (database):
- ``daml.index.db.load_package_entries`` (database):
- ``daml.index.db.load_packages`` (database):
- ``daml.index.db.load_parties`` (database):
- ``daml.index.db.load_party_entries`` (database):
- ``daml.index.db.lookup_active_contract`` (timer):
  - ``daml.index.db.lookup_active_contract`` (database):
  - ``daml.index.db.lookup_active_contract_with_cached_argument`` (database):
- ``daml.index.db.lookup_configuration`` (database):
- ``daml.index.db.lookup_contract_by_key`` (database):
- ``daml.index.db.lookup_flat_transaction_by_id`` (database):
- ``daml.index.db.lookup_key`` (timer):
- ``daml.index.db.lookup_ledger_configuration`` (timer):
- ``daml.index.db.lookup_ledger_end`` (timer):
- ``daml.index.db.lookup_ledger_id`` (timer):
- ``daml.index.db.lookup_maximum_ledger_time`` (timer):
  - ``daml.index.db.lookup_maximum_ledger_time`` (database):
- ``daml.index.db.lookup_transaction_tree_by_id`` (database):
- ``daml.index.db.lookup_transaction`` (timer):
- ``daml.index.db.remove_expired_deduplication_data`` (timer):
  - ``daml.index.db.remove_expired_deduplication_data`` (database):
- ``daml.index.db.stop_deduplicating_command`` (timer):
  - ``daml.index.db.stop_deduplicating_command`` (database):
- ``daml.index.db.store_configuration_entry`` (timer):
  - ``daml.index.db.store_configuration_entry`` (database):
- ``daml.index.db.store_initial_state`` (timer):
  - ``daml.index.db.store_initial_state_from_scenario`` (database):
- ``daml.index.db.store_ledger_entry`` (timer):
- ``daml.index.db.store_package_entry`` (timer):
  - ``daml.index.db.store_package_entry`` (database):
- ``daml.index.db.store_party_entry`` (timer):
  - ``daml.index.db.store_party_entry`` (database):
- ``daml.index.db.store_rejection`` (timer):
  - ``daml.index.db.store_rejection`` (database):
- ``daml.index.db.truncate_all_tables`` (database): time to truncate all tables when using the reset service

Namespace: ``daml.index.db.translation``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``daml.index.db.translation.cache`` (cache): metrics for a cache that keeps track of recently serialized DAML-LF values to speed up their deserialization

Metrics exposed by ``sandbox`` (but not ``sandbox-classic``)
------------------------------------------------------------

Namespace: ``daml.kvutils``
^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``daml.kvutils``

Namespace: ``daml.kvutils.committer``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.last``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.config``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.packageUpload``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.partyAllocation``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.transaction``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.committer.last``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.reader``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.submission``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.validator``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.writer``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.kvutils.conflictdetection``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.ledger.database.queries``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.ledger.database.transactions``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Namespace: ``daml.ledger.log``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``daml.ledger.log.append`` (timer):
- ``daml.ledger.log.read`` (timer):

Namespace: ``daml.ledger.state``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

- ``daml.ledger.state.read`` (timer):
- ``daml.ledger.state.write`` (timer):

Namespace: ``daml.indexer``
^^^^^^^^^^^^^^^^^^^^^^^^^^^

