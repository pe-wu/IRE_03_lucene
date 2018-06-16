import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

class QueryProcessor {
    private MultiFieldQueryParser parser;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;


    QueryProcessor() {
        String[] queryFields = {"title", "plot", "type", "year", "episodetitle"};
        Analyzer analyzer = new StandardAnalyzer();
        parser = new MultiFieldQueryParser(queryFields, analyzer);

        try {
            Directory directory = FSDirectory.open(Paths.get(BooleanQueryLucene.indexPath));
            indexReader = DirectoryReader.open(directory);
            indexSearcher = new IndexSearcher(indexReader);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Set<String> query(String queryString) {
        Set<String> results = new HashSet<>();

        try {
            Query query = parser.parse(queryString);
            TopDocs hits = indexSearcher.search(query, Integer.MAX_VALUE);

            for (ScoreDoc foundDoc : hits.scoreDocs) {
                results.add(indexReader.document(foundDoc.doc).get(BooleanQueryLucene.originalMVLine));
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return results;
    }
}
