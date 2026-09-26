/**
 * One edge in the createNumber() decision-tree CFG.
 */
public class Edge {
    public final String id;
    public final String from;
    public final String to; // target node id, or a leaf id (e.g. "L4")

    public Edge(String id, String from, String to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }
}
