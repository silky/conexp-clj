;; Copyright (c) Daniel Borchmann. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns conexp.io.layouts
  (:use conexp.base
	conexp.io.util
	conexp.layouts.base)
  (:require clojure.string)
  (:import [java.io PushbackReader]))

(ns-doc "Implements IO for layouts.")

;;; Input format dispatch

(define-format-dispatch "layout")
(set-default-layout-format! :simple)

;;; Formats

;; Simple conexp-clj Format for Layout

(add-layout-input-format :simple
			 (fn [rdr]
			   (= "conexp-clj simple" (.readLine rdr))))

(define-layout-output-format :simple
  [layout file]
  (with-out-writer file
    (println "conexp-clj simple")
    (prn {:layout [(positions layout)
		   (connections layout)]})))

(define-layout-input-format :simple
  [file]
  (with-in-reader file
    (let [_        (get-line),
	  hash-map (binding [*in* (PushbackReader. *in*)]
		     (read)),
	  layout   (:layout hash-map)]
      (when-not layout
	(illegal-argument "File " file " does not contain a layout."))
      (apply make-layout layout))))

;;; ConExp Layout format

'TODO

;;; Text

(defn- seq-positions
  "Returns a map from elements of seq to their positions."
  [seq]
  (loop [seq   seq,
         index 0,
         map   {}]
    (if (empty? seq)
      map
      (recur (rest seq)
             (inc index)
             (assoc map (first seq) index)))))

(define-layout-output-format :text
  [layout file]
  (when-not (concept-lattice-layout? layout)
    (illegal-argument "Cannot store layout in :text format which does "
                      "not come from a concept lattice."))
  (let [nodes       (vec (keys (positions layout))),
        node-number (comp inc (seq-positions nodes))]
    (with-out-writer file
      ;; Node
      (doseq [n nodes]
        (let [[x y] ((positions layout) n)]
          (println (str "Node: " (node-number n) ", " x ", " y))))
      ;; Edge
      (doseq [[x y] (connections layout)]
        (println (str "Edge: " (node-number x) ", " (node-number y))))
      ;; Object
      (doseq [n nodes,
              g (first n)]
        (println (str "Object: " (node-number n) ", " g)))
      ;; Attribute
      (doseq [n nodes,
              m (second n)]
        (println (str "Attribute: " (node-number n) ", " m)))
      (println "EOF"))))

(add-layout-input-format :text
                         (fn [_] (re-find #"^Node: " (read-line))))

(defn- get-arguments
  [line start]
  (let [[first args] (rest (re-matches #"^([^:]*): (.*)$" line))]
    (when-not (= first start)
      (illegal-argument "Expected " start ", got " first))
    (clojure.string/split args #", ")))

(define-layout-input-format :text
  [file]
  (let [lines (partition-by #(second (re-matches #"^([^:]*): .*$" %))
                            (line-seq (reader file)))]
    (when-not (<= (count lines) 5)
      (illegal-argument "File " file " does not contain a valid :text layout."))
    (when-not (= (first (last lines)) "EOF")
      (illegal-state "Layout :text format must end with EOF."))
    (let [;; Entries
          entries (apply hash-map (mapcat #(list (second (re-matches #"^([^:]*): .*$" (first %)))
                                                 %)
                                          lines)),
          ;; Node
          pos  (reduce (fn [map line]
                         (let [[node x y] (get-arguments line "Node")]
                           (assoc map node [(Double/parseDouble x), (Double/parseDouble y)])))
                       {}
                       (entries "Node")),
          ;; Edge
          conn (reduce (fn [set line]
                         (conj set (get-arguments line "Edge")))
                       #{}
                       (entries "Edge")),
          ;; Object
          objs (reduce (fn [map line]
                         (let [[node obj] (get-arguments line "Object")]
                           (update-in map [node] conj obj)))
                       {}
                       (entries "Object")),
          ;; Attribute
          atts (reduce (fn [map line]
                         (let [[node att] (get-arguments line "Attribute")]
                           (update-in map [node] conj att)))
                       {}
                       (entries "Attribute")),
          ;; Layout construction
          nodes (map-by-fn (fn [node]
                             [(set (objs node)), (set (atts node))])
                           (keys pos))]
      (make-layout (into {} (for [[number, coord] pos]
                              [(nodes number), coord]))
                   (set-of [(nodes x) (nodes y)]
                           [[x y] conn])))))

;;; TikZ

(define-layout-output-format :tikz
  [layout file]
  (let [vertex-pos      (positions layout),
        sorted-vertices (sort #(let [[x_1 y_1] (vertex-pos %1),
                                     [x_2 y_2] (vertex-pos %2)]
                                 (or (< y_1 y_2)
                                     (and (= y_1 y_2)
                                          (< x_1 x_2))))
                              (nodes layout)),
        vertex-idx      (into {}
                              (map-indexed (fn [i v] [v i])
                                           sorted-vertices))]
    (with-out-writer file
      (println "\\colorlet{mivertexcolor}{blue}")
      (println "\\colorlet{jivertexcolor}{red}")
      (println "\\colorlet{vertexcolor}{mivertexcolor!50}")
      (println "\\colorlet{bordercolor}{black!80}")
      (println "\\colorlet{linecolor}{gray}")
      (println "\\tikzset{vertexbase/.style={semithick, shape=circle, inner sep=2pt, outer sep=0pt, draw=bordercolor},%")
      (println "  vertex/.style={vertexbase, fill=vertexcolor!45},%")
      (println "  mivertex/.style={vertexbase, fill=mivertexcolor!45},%")
      (println "  jivertex/.style={vertexbase, fill=jivertexcolor!45},%")
      (println "  divertex/.style={vertexbase, top color=mivertexcolor!45, bottom color=jivertexcolor!45},%")
      (println "  conn/.style={-, thick, color=linecolor}%")
      (println "}")
      (println "\\begin{tikzpicture}")
      (println "  \\begin{scope} %for scaling and the like")
      (println "    \\begin{scope} %draw vertices")
      (println "      \\foreach \\nodename/\\nodetype/\\xpos/\\ypos in {%")
      (let [infs         (set (inf-irreducibles layout)),
            sups         (set (sup-irreducibles layout)),
            insu         (intersection infs sups),
            vertex-lines (map (fn [v]
                                (let [i     (vertex-idx v),
                                      [x y] (vertex-pos v)]
                                  (str "        " i "/"
                                       (cond
                                        (contains? insu v)  "divertex"
                                        (contains? sups v)  "jivertex"
                                        (contains? infs v)  "mivertex"
                                        :else               "vertex")
                                       "/" x "/" y)))
                              sorted-vertices)]
        (doseq [x (interpose ",\n" vertex-lines)]
          (print x))
        (println))
      (println "      } \\nodex[\\nodetype] (\\nodename) at (\\xpos, \\ypos) {};")
      (println "    \\end{scope}")
      (println "    \\begin{scope} %draw connections")
      (doseq [[v w] (connections layout)]
        (println (str "      \\path (" (vertex-idx v) ") edge[conn] (" (vertex-idx w) ");")))
      (println "    \\end{scope}")
      (println "    \\begin{scope} %add labels")
      (println "      \\foreach \\nodename/\\labelpos/\\labelopts/\\labelcontent in {%")
      (let [ann       (annotation lay),
            ann-lines (mapcat (fn [v]
                                (let [[u l] (ann v),
                                      lines (if-not (= "" u)
                                              (list (str "        " (vertex-idx v) "/above//{" u "}"))
                                              ()),
                                      lines (if-not (= "" l)
                                              (conj lines
                                                    (str "        " (vertex-idx v) "/below//{" l "}"))
                                              lines)]
                                    lines))
                              sorted-vertices)]
        (doseq [x (interpose ",\n" ann-lines)]
          (print x))
        (println))
      (println "      } \\coordinate[label={[\\labelopts]\\labelpos:{\\labelcontent}}](c) at (\\nodename);")
      (println "    \\end{scope}")
      (println "  \\end{scope}")
      (println "\\end{tikzpicture}"))))

;;; FCA-style

(define-layout-output-format :fca-style
  [layout file]
  (unsupported-operation "Output in :fca-style is not yet supported."))

;;;

nil
