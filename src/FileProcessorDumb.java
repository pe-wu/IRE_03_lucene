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

class FileProcessorDumb {

    private static IndexWriter writer;
    private static String titleLine = null;
    private static StringBuilder plotText = new StringBuilder();

    private static String pattern =
            "mv {2}(.+) \\(([ \\d]{4}[^)]*)\\) ?(?:\\{([^}]*)})?(\\(v\\))?(\\(vg\\))?(\\(tv\\))?";
    private static Pattern regEx = Pattern.compile(pattern);

    private static String MOVIE = "movie";
    private static String SERIES = "series";
    private static String EPISODE = "episode";
    private static String VIDEO = "video";
    private static String VIDEOGAME = "videogame";
    private static String TELEVISION = "television";

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
        Document doc = new Document();

        //saveOriginalMVLine
        doc.add(new StoredField(BooleanQueryLucene.originalMVLine, titleLine));
        titleLine = titleLine.substring(4); //MV:_ deleted

        //saveType
        String type = extractType(titleLine);
        doc.add(new StringField("type", type, Field.Store.NO));
        titleLine = titleLine.toLowerCase();

        //saveEpisodeTitle
        doc.add(new TextField("episodetitle",
                doc.get("type").equals("episode") ? extractEpisodeTitle(titleLine) : "",
                Field.Store.NO));

        //eraseAdditionalInfo
        if (type.equals(EPISODE)) {
            int slicingIndex = titleLine.lastIndexOf("{");
            titleLine = titleLine.substring(0, slicingIndex - 1);
        } else if (type.equals(VIDEO) || type.equals(VIDEOGAME) || type.equals(TELEVISION)) {
            int slicingIndex = titleLine.lastIndexOf("(");
            titleLine = titleLine.substring(0, slicingIndex - 1);
        }

        //saveYear
        int yearBegin = titleLine.lastIndexOf("(") + 1;
        String year = titleLine.substring(yearBegin, yearBegin + 4);
        doc.add(new StringField("year", year, Field.Store.NO));

        //eraseYear
        int slicingIndex = titleLine.lastIndexOf("(");
        titleLine = titleLine.substring(0, slicingIndex - 1);

        //saveTitle
        String title = (type.equals(EPISODE) || type.equals(SERIES)) ?
                titleLine.substring(1, titleLine.length() - 1) :
                titleLine;
        doc.add(new TextField("title", title, Field.Store.NO));

        //savePlot
        doc.add(new TextField("plot", plotText.toString(), Field.Store.NO));
        writer.addDocument(doc);
    }

    private String extractType(String titleLine) {
        if (titleLine.startsWith("\"")) {
            return titleLine.endsWith("}") ? EPISODE : SERIES;
        }
        if (hasYearAtEnd(titleLine)) {
            return MOVIE;
        }
        if (titleLine.endsWith("(TV)")) {
            return "television";
        }
        if (titleLine.endsWith("(V)")) {
            return "video";
        }
        if (titleLine.endsWith("(VG)")) {
            return "videogame";
        }
        return MOVIE; //The movies like "#Selfie (2014/II)" are meant to be movies too.
    }

    private String extractEpisodeTitle(String titleLine) {
        int indexOfEpisodeTitle = titleLine.lastIndexOf("{");
        return titleLine.substring(indexOfEpisodeTitle + 1, titleLine.length() - 1);
    }

    private boolean hasYearAtEnd(String titleLine) {
        char lastButOne = titleLine.charAt(titleLine.length() - 2);
        return Character.isDigit(lastButOne) || lastButOne == '?';
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
