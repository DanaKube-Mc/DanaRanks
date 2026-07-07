package su.nightexpress.excellentjobs.job.model;

public class Job {
    private final String id;
    private final String name;

    public Job(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }
}
