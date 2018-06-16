import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

class FileProcessor {

    private static IndexWriter writer;
    private static String titleLine = null;
    private static StringBuilder plotText = new StringBuilder();

    private static String pattern =
            "mv {2}(.+) \\(([ \\d]{4}[^)]*)\\) ?(?:\\{([^}]*)})?(\\(v\\))?(\\(vg\\))?(\\(tv\\))?";
    private static Pattern regEx = Pattern.compile(pattern);

    void buildIndices(Path plotFile) {

        try {
            Analyzer analyzer = new StandardAnalyzer();
            Directory directory = FSDirectory.open(Paths.get(BooleanQueryLucene.indexPath));
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            writer = new IndexWriter(directory, iwc);

            indexDocs(plotFile);

            writer.forceMerge(1);
            writer.commit();
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void indexDocs(Path plotFile) throws IOException {
        Stream<String> lines = Files.lines(plotFile, ISO_8859_1);
        lines.forEachOrdered(processLine);
        processLine.accept("--------"); //last entry will be processed too
    }

    private Consumer<String> processLine = (line) -> {
        if (line.startsWith("PL")) {
            savePlotLine(line);
        } else if (line.startsWith("MV")) {
            saveTitleLine(line);
        } else if (line.startsWith("-")) {
            if (titleLine != null) {
                try {
                    saveDocument();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            clearProcessor();
        }
    };

    private void saveDocument() throws IOException {
        Matcher m = regEx.matcher(normalize(titleLine));

        if (m.find()) {
            Document doc = new Document();
            doc.add(new StoredField(BooleanQueryLucene.originalMVLine, titleLine));
            doc.add(new StringField("type", evaluateDocType(m), Field.Store.NO));
            doc.add(new StringField("year", m.group(2), Field.Store.NO));

            doc.add(new TextField("title", withoutQuotationMarks(m.group(1)), Field.Store.NO));
            doc.add(new TextField("plot", plotText.toString(), Field.Store.NO));
            if (m.group(3) != null) {
                doc.add(new TextField("episodetitle", m.group(3), Field.Store.NO));
            }

            writer.addDocument(doc);
        }
    }

    private String evaluateDocType(Matcher m) {
        if (m.group(3) != null) {
            return "episode";
        }
        if (m.group(4) != null) {
            return "video";
        }
        if (m.group(5) != null) {
            return "videogame";
        }
        if (m.group(6) != null) {
            return "television";
        }
        if (m.group(1).charAt(0) != '\"') {
            return "movie";
        }
        return "series";
    }

    private String withoutQuotationMarks(String text) {
        return (text.charAt(0) == '\"') ? text.substring(1, text.length() - 1) : text;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ENGLISH).replaceAll("[.,:!?]", " ");
    }

    private void saveTitleLine(String line) {
        titleLine = line;
    }

    private void savePlotLine(String line) {
        plotText.append(line.substring(4)); //erase PL:_
        plotText.append(" ");
    }

    private void clearProcessor() {
        titleLine = null;
        plotText.setLength(0);
    }

}
