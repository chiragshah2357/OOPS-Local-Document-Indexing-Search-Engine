package docsearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the .txt files of a folder (and its sub folders) into Document objects.
 * This is the file handling part of the project.
 */
public class DocumentLoader {

    private int nextId = 1;
    private final List<String> skipped = new ArrayList<String>();

    /**
     * Loads every .txt file under the folder.
     *
     * @throws IOException if the path is not a folder
     */
    public List<Document> loadFolder(String folderPath) throws IOException {
        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            throw new IOException("Not a folder: " + folderPath);
        }

        nextId = 1;
        skipped.clear();

        List<Document> documents = new ArrayList<Document>();
        collect(folder, documents);
        return documents;
    }

    /** Paths of files that could not be read during the last load. */
    public List<String> getSkipped() {
        return new ArrayList<String>(skipped);
    }

    // Visits a folder: reads the .txt files in it and calls itself for each sub folder (recursion).
    private void collect(File folder, List<Document> documents) {
        File[] items = folder.listFiles();
        if (items == null) {
            skipped.add(folder.getPath()); // the folder itself cannot be read
            return;
        }
        Arrays.sort(items); // a fixed order means the same ids on every run

        for (File item : items) {
            if (item.isDirectory()) {
                collect(item, documents);
            } else if (item.getName().toLowerCase().endsWith(".txt")) {
                try {
                    documents.add(readFile(item));
                } catch (IOException e) {
                    skipped.add(item.getPath());
                }
            }
        }
    }

    // Reads one file line by line and puts the lines back together.
    private Document readFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder text = new StringBuilder();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        } finally {
            reader.close(); // always close the file, even if reading failed
        }
        return new Document(nextId++, file.getName(), file.getPath(), text.toString());
    }
}
