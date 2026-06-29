package tblack.stackwise.stack;

public interface ItemStackLimitAdapter {
    int read(Object item) throws ReflectiveOperationException;

    void write(Object item, int value) throws ReflectiveOperationException;

    boolean isAvailable();

    String description();
}
