package docsearch;

/**
 * One text file that has been read into memory.
 *
 * The fields are private and final, so other classes can only read them
 * through the getters. That is encapsulation: once a Document is created
 * nobody can change it by accident.
 */
public class Document {
    private final int id;
    private final String title;
    private final String path;
    private final String text;

    public Document(int id, String title, String path, String text) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.text = text;
    }

    /** A number that identifies this document inside the index. */
    public int getId() {
        return id;
    }

    /** The file name, for example java-collections.txt. */
    public String getTitle() {
        return title;
    }

    /** Where the file is on disk. */
    public String getPath() {
        return path;
    }

    /** The whole content of the file. */
    public String getText() {
        return text;
    }
}
