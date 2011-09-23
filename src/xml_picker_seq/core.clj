(ns xml-picker-seq.core
  (:use [clojure.contrib.seq-utils :only [fill-queue]]
        [clojure.contrib.duck-streams :only [reader]]))

(defn root-element? [#^nu.xom.Element element]
  (instance? nu.xom.Document (.getParent element)))

(defn- extract [#^java.io.Reader rdr record-tag-name extract-fn enqueue]
  (let [empty (nu.xom.Nodes.)
        keep? (atom false)
        factory (proxy [nu.xom.NodeFactory] []

                  (startMakingElement [name ns]
                    (when (= name record-tag-name)
                      (reset! keep? true))
                      (let [#^nu.xom.NodeFactory this this]
                        (proxy-super startMakingElement name ns)))

                  (finishMakingElement [#^nu.xom.Element element]
                    (when (= (.getLocalName element) record-tag-name)
                      (when-let [value (extract-fn element)]
                        (enqueue value))
                      (reset! keep? false))

                    (if (or @keep? (root-element? element))
                      (let [#^nu.xom.NodeFactory this this]
                        (proxy-super finishMakingElement element))
                      empty)))]
    (.build (nu.xom.Builder. factory)
            rdr)))

(defn xml-picker-seq [rdr record-tag-name extract-fn]
  (fill-queue (fn [fill] (extract rdr record-tag-name extract-fn fill))
              {:queue-size 128}))

(defn xpath-query
  "Takes a XPath query string and optionally a context object, extract-fn
   to run on each result node and final-fn to run on the results.
   Returns a function that takes the element to run the query on."
  [query & {:keys [context extract-fn final-fn]
            :or {context nil extract-fn #(.getValue %) final-fn identity}}]
  (fn [element]
    (let [nodes (.query element query context)]
      (final-fn (map #(extract-fn (.get nodes %)) (range (.size nodes)))))))

(comment
  (with-open [rdr (clojure.contrib.duck-streams/reader "http://www.loc.gov/standards/marcxml/xml/collection.xml")]
    (let [context (nu.xom.XPathContext. "marc" "http://www.loc.gov/MARC21/slim")
          titles (xml-picker-seq.core/xml-picker-seq
                  rdr "record"
                  (xml-picker-seq.core/xpath-query "//marc:datafield[@tag = '245']/marc:subfield[@code = 'a']"
                                                   :context context :final-fn first))]
      (doseq [title titles]
        (println (take 10 s))))))