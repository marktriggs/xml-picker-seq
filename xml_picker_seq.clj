(ns net.dishevelled.xml-picker-seq
  (:use clojure.contrib.duck-streams)
  (:use clojure.contrib.seq-utils))



(defn root-element? [element]
  (instance? nu.xom.Document (.getParent element)))


(defn- extract [rdr record-tag-name extract-fn enqueue]
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
                      (println element)
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



(comment
;; using it!
(with-open [rdr (reader "/home/mtriggs/smaller.xml")]
  (let [context (nu.xom.XPathContext. "marc" "http://www.loc.gov/MARC21/slim")]
    (let [s (xml-picker-seq rdr "record"
                            (fn [element]
                              {:title (.. element
                                          (query
                                           "//marc:datafield[@tag = '245']/marc:subfield[@code = 'a']" context)
                                          (get 0)
                                          getValue)}))]
      (doall s))))
)
