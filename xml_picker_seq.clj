(ns net.dishevelled.xml-picker-seq
  (:use clojure.contrib.duck-streams)
  (:use clojure.contrib.seq-utils))

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



(comment
  (with-open [#^java.io.Reader rdr (reader "/home/mst/updates.xml")]
    (let [context (nu.xom.XPathContext. "marc" "http://www.loc.gov/MARC21/slim")]
      (let [s (xml-picker-seq rdr "record"
			      (fn [#^nu.xom.Element element]
				{:title (.. element
					    (query
					     "//marc:datafield[@tag = '245']/marc:subfield[@code = 'a']" context)
					    (get 0)
					    getValue)}))]
	(println (take 10 s))))))

