;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.tests
  (:use [conexp.base :only (defvar-)])
  (:use [clojure.test :only (*stack-trace-depth* run-tests)]))


;;;

(defvar- *testing-namespaces* '[conexp.tests.util
				conexp.tests.base
				conexp.tests.fca.contexts
				conexp.tests.fca.implications])

(apply require *testing-namespaces*)

(defn test-conexp
  "Run tests for conexp."
  []
  (binding [*stack-trace-depth* 13]
    (apply run-tests *testing-namespaces*)
    nil))

;;;

nil
