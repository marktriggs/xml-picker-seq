xml-picker-seq
==============

This wrapper tries to make it easy to pull out interesting bits of large
XML files in a space-efficient way.

Almost every XML file I ever look at seems to be large (>1GB) and
oriented around records (MARC records, Solr documents, etc.).  Generally
all I want to do is:

  - Read the next record
  - Pull out a few of its fields
  - Throw the rest away, rinse and repeat

so that's what this does, using XOM and a queue behind the scenes.

Example usage
-------------

Parsing a Solr response
-----------------------

    <response>
      <lst name="responseHeader">
        <int name="status">0</int>
        <int name="QTime">0</int>
      </lst>
      <result name="response" numFound="170" start="0">
        <doc>
          <arr name="author"><str>Hardie &amp; Gorman Pty. Ltd</str></arr>
          <arr name="callnumber"><str>MAP FOLDER 176, LFSP 2757</str></arr>
          <arr name="title"><str>Important clearance sale of valuable city, suburban &amp; country properties, at upset prices : including investment, residential and vacant properties, building, farming, agricultural, and grazing lands ... Auction sale, Wednesday, 8th December, 1897 / Hardie &amp; Gorman, Auctioneers</str></arr>
        </doc>
        <doc>
          <arr name="author"><str>Hamburg, Harold G</str></arr>
          <arr name="callnumber"><str>PAM BOX 429</str></arr>
          <arr name="title"><str>The legislation of Richard III</str></arr>
        </doc>
        ...
      </result>
    </response>

to extract a list of authors:

    (with-open [rdr (clojure.contrib.duck-streams/reader (java.net.URL. "http://my.host/solr/select?q=my+query"))]
      (doall (apply concat
                    (xml-picker-seq.core/xml-picker-seq
                     rdr
                    "doc"
                    (xml-picker-seq.core/xpath-query "arr[@name='author']/str")))))

This:

  - Walks through the document parsing (and loading into memory) one
    full "doc" node at a time

  - Uses XPath to pull out all of the str values for "author"

  - Gets the string values for each author node and returns them


Parsing MARCXML records
-----------------------

Slightly more fiddly because of the namespaces, but much the same:

    (with-open [rdr (clojure.contrib.duck-streams/reader "http://www.loc.gov/standards/marcxml/xml/collection.xml")]
      (let [context (nu.xom.XPathContext. "marc" "http://www.loc.gov/MARC21/slim")
            titles (xml-picker-seq.core/xml-picker-seq
                    rdr "record"
                    (xml-picker-seq.core/xpath-query "//marc:datafield[@tag = '245']/marc:subfield[@code = 'a']"
                                                     :context context :final-fn first))]
        (doseq [title titles]
          (do-something-with title))))

## License

Copyright (C) 2009 Mark Triggs

Distributed under the Eclipse Public License. See the file "COPYING".
