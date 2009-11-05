(ns net.dishevelled.xml-picker-seq
  (:import (java.util.concurrent LinkedBlockingQueue)
           (java.util.concurrent TimeUnit))
  (:use clojure.contrib.duck-streams))


(defn- enqueue [#^LinkedBlockingQueue queue elt]
  (.offer queue
          elt
          Long/MAX_VALUE
          TimeUnit/SECONDS))


(defn- extract [rdr record-tag-name extract-fn queue]
  (let [empty (nu.xom.Nodes.)
        keep? (atom false)
        factory (proxy [nu.xom.NodeFactory] []


                  (startMakingElement [name ns]
                    (when (= name record-tag-name)
                      (swap! keep? (constantly true)))
                      (let [#^nu.xom.NodeFactory this this]
                        (proxy-super startMakingElement name ns)))


                  (finishMakingElement [#^nu.xom.Element element]
                    (when (= (.getLocalName element) record-tag-name)
                      (let [value (extract-fn element)]
                        (when (not (nil? value))
                          (enqueue queue value)))
                      (swap! keep? (constantly false)))

                    (if (or @keep?
                            (instance? nu.xom.Document (.getParent element)))
                      (let [#^nu.xom.NodeFactory this this]
                        (proxy-super finishMakingElement element))
                      empty)))]
    (try (.build (nu.xom.Builder. factory)
                 rdr)
         (finally (enqueue queue :EOF)))))


(defn- queue-seq [#^LinkedBlockingQueue queue #^Thread producer]
  (lazy-seq
    (let [elt (.take queue)]
      (if (= elt :EOF)
        (do (.join producer)
            nil)
        (cons elt (queue-seq queue producer))))))


(defn xml-picker-seq [rdr record-tag-name extract-fn]
  (let [queue (LinkedBlockingQueue. 100)
        producer (doto (Thread. (fn []
                                  (try
                                   (extract rdr record-tag-name extract-fn queue)
                                   (catch Exception _))))
                   (.start))]
    (queue-seq queue producer)))



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
