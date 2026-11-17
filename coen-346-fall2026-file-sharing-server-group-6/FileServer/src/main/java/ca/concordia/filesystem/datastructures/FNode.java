package ca.concordia.filesystem.datastructures;

public class FNode {

    private short blockIndex; // index of the data block; negative if the block is not in use
    private short next;       // index of the next fnode in the file; -1 if there is none

    public FNode(short blockIndex) {
        this.blockIndex = blockIndex;
        this.next = -1;
    }

    // Getters and Setters
    public short getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(short blockIndex) {
        this.blockIndex = blockIndex;
    }

    public short getNext() {
        return next;
    }

    public void setNext(short next) {
        this.next = next;
    }
}
