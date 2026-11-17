package ca.concordia.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private RandomAccessFile disk; // the file that stores the simulated file system
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    // Sizes of the metadata structures when they are stored on the disk
    private static final int FILENAME_LENGTH = 11;
    private static final int FENTRY_SIZE = FILENAME_LENGTH + 2 + 2; // name + filesize + firstBlock
    private static final int FNODE_SIZE = 2 + 2;                     // blockIndex + next

    private FEntry[] inodeTable; // Array of inodes; a null slot is a free file entry
    private FNode[] fnodeTable;  // fnode i describes data block i; a negative blockIndex means the block is free
    private int metadataBlocks;  // number of blocks at the start of the disk that hold the metadata

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            inodeTable = new FEntry[MAXFILES];
            fnodeTable = new FNode[MAXBLOCKS];

            int metadataBytes = MAXFILES * FENTRY_SIZE + MAXBLOCKS * FNODE_SIZE;
            metadataBlocks = (metadataBytes + BLOCK_SIZE - 1) / BLOCK_SIZE; // round up
            if (metadataBlocks >= MAXBLOCKS) {
                throw new IllegalStateException("The metadata uses every block; no space is left for file data.");
            }

            // Metadata blocks are always in use (positive index); every data block starts free (negative index).
            for (int i = 0; i < MAXBLOCKS; i++) {
                fnodeTable[i] = new FNode((short) (i < metadataBlocks ? i : -i));
            }

            try {
                disk = new RandomAccessFile(filename, "rw");
                disk.setLength(0);
                disk.write(new byte[MAXBLOCKS * BLOCK_SIZE]);
                saveMetadata();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create the file system in " + filename, e);
            }

            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
        if (fileName.length() > FILENAME_LENGTH) {
            throw new Exception("ERROR: filename too large");
        }
        if (findEntryIndex(fileName) != -1) {
            throw new Exception("ERROR: file " + fileName + " already exists");
        }

        int freeEntry = -1;
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i] == null) {
                freeEntry = i;
                break;
            }
        }
        if (freeEntry == -1) {
            throw new Exception("ERROR: maximum number of files reached");
        }

        inodeTable[freeEntry] = new FEntry(fileName, (short) 0, (short) -1);
        saveMetadata();
    }

    public void deleteFile(String fileName) throws Exception {
        int entryIndex = findEntryIndex(fileName);
        if (entryIndex == -1) {
            throw new Exception("ERROR: file " + fileName + " does not exist");
        }

        freeBlocks(inodeTable[entryIndex].getFirstBlock());
        inodeTable[entryIndex] = null;
        saveMetadata();
    }

    public void writeFile(String fileName, byte[] contents) throws Exception {
        int entryIndex = findEntryIndex(fileName);
        if (entryIndex == -1) {
            throw new Exception("ERROR: file " + fileName + " does not exist");
        }
        FEntry entry = inodeTable[entryIndex];

        int blocksNeeded = (contents.length + BLOCK_SIZE - 1) / BLOCK_SIZE; // round up
        // The old content is replaced, so the blocks the file already owns count as available.
        int blocksAvailable = countFreeBlocks() + countBlocks(entry.getFirstBlock());
        if (contents.length > Short.MAX_VALUE || blocksNeeded > blocksAvailable) {
            throw new Exception("ERROR: file too large");
        }

        // Nothing may be modified before this point, so that a failed write leaves the file system unchanged.
        freeBlocks(entry.getFirstBlock());

        short firstBlock = -1;
        int previousBlock = -1;
        for (int i = 0; i < blocksNeeded; i++) {
            int block = findFreeBlock();
            writeBlock(block, Arrays.copyOfRange(contents, i * BLOCK_SIZE, (i + 1) * BLOCK_SIZE));

            fnodeTable[block].setBlockIndex((short) block);
            fnodeTable[block].setNext((short) -1);
            if (previousBlock == -1) {
                firstBlock = (short) block;
            } else {
                fnodeTable[previousBlock].setNext((short) block);
            }
            previousBlock = block;
        }

        entry.setFilesize((short) contents.length);
        entry.setFirstBlock(firstBlock);
        saveMetadata();
    }

    // TODO: Add readFile, listFiles and other required methods,

    /** Returns the index of the file entry with this name, or -1 if there is no such file. */
    private int findEntryIndex(String fileName) {
        for (int i = 0; i < MAXFILES; i++) {
            if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                return i;
            }
        }
        return -1;
    }

    /** Returns the lowest-numbered free data block, or -1 if the disk is full. */
    private int findFreeBlock() {
        for (int i = metadataBlocks; i < MAXBLOCKS; i++) {
            if (fnodeTable[i].getBlockIndex() < 0) {
                return i;
            }
        }
        return -1;
    }

    private int countFreeBlocks() {
        int count = 0;
        for (int i = metadataBlocks; i < MAXBLOCKS; i++) {
            if (fnodeTable[i].getBlockIndex() < 0) {
                count++;
            }
        }
        return count;
    }

    /** Counts the blocks in the chain of fnodes that starts at firstBlock. */
    private int countBlocks(short firstBlock) {
        int count = 0;
        for (short current = firstBlock; current != -1; current = fnodeTable[current].getNext()) {
            count++;
        }
        return count;
    }

    /** Zeroes every block in the chain that starts at firstBlock and marks its fnodes as free. */
    private void freeBlocks(short firstBlock) throws IOException {
        short current = firstBlock;
        while (current != -1) {
            FNode node = fnodeTable[current];
            short next = node.getNext();

            writeBlock(current, new byte[BLOCK_SIZE]);
            node.setBlockIndex((short) -current);
            node.setNext((short) -1);

            current = next;
        }
    }

    /** Writes exactly one block of data (data.length must be BLOCK_SIZE). */
    private void writeBlock(int block, byte[] data) throws IOException {
        disk.seek((long) block * BLOCK_SIZE);
        disk.write(data);
    }

    /** Writes the fentry array followed by the fnode array to the start of the disk. */
    private void saveMetadata() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAXFILES * FENTRY_SIZE + MAXBLOCKS * FNODE_SIZE);

        for (FEntry entry : inodeTable) {
            byte[] name = new byte[FILENAME_LENGTH];
            short size = 0;
            short firstBlock = -1;
            if (entry != null) {
                byte[] nameBytes = entry.getFilename().getBytes(StandardCharsets.UTF_8);
                System.arraycopy(nameBytes, 0, name, 0, Math.min(nameBytes.length, FILENAME_LENGTH));
                size = entry.getFilesize();
                firstBlock = entry.getFirstBlock();
            }
            buffer.put(name).putShort(size).putShort(firstBlock);
        }

        for (FNode node : fnodeTable) {
            buffer.putShort(node.getBlockIndex()).putShort(node.getNext());
        }

        disk.seek(0);
        disk.write(buffer.array());
    }
}