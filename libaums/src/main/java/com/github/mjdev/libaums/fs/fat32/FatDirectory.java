/*
 * (C) Copyright 2014-2016 mjahnen <jahnen@in.tum.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.github.mjdev.libaums.fs.fat32;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.atech.library.usb.libaums.data.LibAumsException;
import lombok.extern.slf4j.Slf4j;

import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.fs.UsbFile;

/**
 * This class represents a directory in the FAT32 file system. It can hold other
 * directories and files.
 * 
 * @author mjahnen
 * 
 */
@Slf4j
public class FatDirectory implements UsbFile {

	private ClusterChain chain;
	private BlockDeviceDriver blockDevice;
	private FAT fat;
	private Fat32BootSector bootSector;
	/**
	 * Entries read from the device.
	 */
	private List<FatLfnDirectoryEntry> entries;
	/**
	 * Map for checking for existence when for example creating new files or
	 * directories.
	 * <p>
	 * All items are stored in lower case because a FAT32 fs is not case
	 * sensitive.
	 */
	private Map<String, FatLfnDirectoryEntry> lfnMap;
	/**
	 * Map for checking for existence of short names when generating short names
	 * for new files or directories.
	 */
	private Map<ShortName, FatDirectoryEntry> shortNameMap;
	/**
	 * Null if this is the root directory.
	 */
	private FatDirectory parent;
	/**
	 * Null if this is the root directory.
	 */
	private FatLfnDirectoryEntry entry;

	private String volumeLabel;

	/**
	 * Constructs a new FatDirectory with the given information.
	 * 
	 * @param blockDevice
	 *            The block device the fs is located.
	 * @param fat
	 *            The FAT of the fs.
	 * @param bootSector
	 *            The boot sector if the fs.
	 * @param parent
	 *            The parent directory of the newly created one.
	 */
	private FatDirectory(BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector,
			FatDirectory parent) {
		this.blockDevice = blockDevice;
		this.fat = fat;
		this.bootSector = bootSector;
		this.parent = parent;
		lfnMap = new HashMap<String, FatLfnDirectoryEntry>();
		shortNameMap = new HashMap<ShortName, FatDirectoryEntry>();
	}

	/**
	 * This method creates a new directory from a given
	 * {@link FatDirectoryEntry}.
	 * 
	 * @param entry
	 *            The entry of the directory.
	 * @param blockDevice
	 *            The block device the fs is located.
	 * @param fat
	 *            The FAT of the fs.
	 * @param bootSector
	 *            The boot sector if the fs.
	 * @param parent
	 *            The parent directory of the newly created one.
	 * @return Newly created directory.
	 */
	/* package */static FatDirectory create(FatLfnDirectoryEntry entry,
			BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector, FatDirectory parent) {
		FatDirectory result = new FatDirectory(blockDevice, fat, bootSector, parent);
		result.entry = entry;
		return result;
	}

	/**
	 * Reads the root directory from a FAT32 file system.
	 * 
	 * @param blockDevice
	 *            The block device the fs is located.
	 * @param fat
	 *            The FAT of the fs.
	 * @param bootSector
	 *            The boot sector if the fs.
	 * @return Newly created root directory.
	 * @throws IOException
	 *             If reading from the device fails.
	 */
	/* package */static FatDirectory readRoot(BlockDeviceDriver blockDevice, FAT fat,
			Fat32BootSector bootSector) throws LibAumsException {
		FatDirectory result = new FatDirectory(blockDevice, fat, bootSector, null);
		result.chain = new ClusterChain(bootSector.getRootDirStartCluster(), blockDevice, fat,
				bootSector);
		result.init(); // init calls readEntries
		return result;
	}

	/**
	 * Initializes the {@link FatDirectory}. Creates the cluster chain if needed
	 * and reads all entries from the cluster chain.
	 * 
	 * @throws IOException
	 *             If reading from the device fails.
	 */
	private void init() throws LibAumsException {
		if (chain == null) {
			chain = new ClusterChain(entry.getStartCluster(), blockDevice, fat, bootSector);
		}

		// entries is allocated here
		// an exception will be thrown if entries is used before the directory has been initialised
		// use of uninitialised entries can lead to data loss!
		if(entries == null) {
			entries = new ArrayList<FatLfnDirectoryEntry>();
		}

		// only read entries if we have no entries
		// otherwise newly created directories (. and ..) will read trash data
		if(entries.size() == 0) {
			readEntries();
		}
	}

	/**
	 * Reads all entries from the directory and saves them into {@link #lfnMap},
	 * {@link #entries} and {@link #shortNameMap}.
	 * 
	 * @throws IOException
	 *             If reading from the device fails.
	 * @see #write()
	 */
	private void readEntries() throws LibAumsException {
		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		chain.read(0, buffer);
		// we have to buffer all long filename entries to parse them later
		ArrayList<FatDirectoryEntry> list = new ArrayList<FatDirectoryEntry>();
		buffer.flip();
		while (buffer.remaining() > 0) {
			FatDirectoryEntry e = FatDirectoryEntry.read(buffer);
			if (e == null) {
				break;
			}

			if (e.isLfnEntry()) {
				list.add(e);
				continue;
			}

			if (e.isVolumeLabel()) {
				if (!isRoot()) {
					log.warn( "volume label in non root dir!");
				}
				volumeLabel = e.getVolumeLabel();
				log.debug( "volume label: " + volumeLabel);
				continue;
			}

			if (e.isHidden()) {
				log.debug( "hidden "+e.isHidden() );
				continue;
			}


			// we just skip deleted entries
			if (e.isDeleted()) {
				list.clear();
				continue;
			}

			FatLfnDirectoryEntry lfnEntry = FatLfnDirectoryEntry.read(e, list);
			addEntry(lfnEntry, e);
			list.clear();
		}
	}

	/**
	 * Adds the long file name entry to {@link #lfnMap} and {@link #entries} and
	 * the actual entry to {@link #shortNameMap}.
	 * <p>
	 * This method does not write the changes to the disk. If you want to do so
	 * call {@link #write()} after adding an entry.
	 * 
	 * @param lfnEntry
	 *            The long filename entry to add.
	 * @param entry
	 *            The corresponding short name entry.
	 * @see #removeEntry(FatLfnDirectoryEntry)
	 */
	private void addEntry(FatLfnDirectoryEntry lfnEntry, FatDirectoryEntry entry) {
		entries.add(lfnEntry);
		lfnMap.put(lfnEntry.getName().toLowerCase(Locale.getDefault()), lfnEntry);
		shortNameMap.put(entry.getShortName(), entry);
	}

	/**
	 * Removes (if existing) the long file name entry from {@link #lfnMap} and
	 * {@link #entries} and the actual entry from {@link #shortNameMap}.
	 * <p>
	 * This method does not write the changes to the disk. If you want to do so
	 * call {@link #write()} after adding an entry.
	 * 
	 * @param lfnEntry
	 *            The long filename entry to remove.
	 * @see #addEntry(FatLfnDirectoryEntry, FatDirectoryEntry)
	 */
	/* package */void removeEntry(FatLfnDirectoryEntry lfnEntry) {
		entries.remove(lfnEntry);
		lfnMap.remove(lfnEntry.getName().toLowerCase(Locale.getDefault()));
		shortNameMap.remove(lfnEntry.getActualEntry().getShortName());
	}

	/**
	 * Renames a long filename entry to the desired new name.
	 * <p>
	 * This method immediately writes the change to the disk, thus no further
	 * call to {@link #write()} is needed.
	 * 
	 * @param lfnEntry
	 *            The long filename entry to rename.
	 * @param newName
	 *            The new name.
	 * @throws IOException
	 *             If writing the change to the disk fails.
	 */
	/* package */void renameEntry(FatLfnDirectoryEntry lfnEntry, String newName) throws LibAumsException {
		if (lfnEntry.getName().equals(newName))
			return;

		removeEntry(lfnEntry);
		lfnEntry.setName(newName,
				ShortNameGenerator.generateShortName(newName, shortNameMap.keySet()));
		addEntry(lfnEntry, lfnEntry.getActualEntry());
		write();
	}

	/**
	 * Writes the {@link #entries} to the disk. Any changes made by
	 * {@link #addEntry(FatLfnDirectoryEntry, FatDirectoryEntry)} or
	 * {@link #removeEntry(FatLfnDirectoryEntry)} will then be committed to the
	 * device.
	 * 
	 * @throws IOException
	 * @see {@link #write()}
	 */
	/* package */void write() throws LibAumsException {
		init();
		final boolean writeVolumeLabel = isRoot() && volumeLabel != null;
		// first lookup the total entries needed
		int totalEntryCount = 0;
		for (FatLfnDirectoryEntry entry : entries) {
			totalEntryCount += entry.getEntryCount();
		}

		if (writeVolumeLabel)
			totalEntryCount++;

		long totalBytes = totalEntryCount * FatDirectoryEntry.SIZE;
		chain.setLength(totalBytes);

		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		if (writeVolumeLabel)
			FatDirectoryEntry.createVolumeLabel(volumeLabel).serialize(buffer);

		for (FatLfnDirectoryEntry entry : entries) {
			entry.serialize(buffer);
		}

		if (totalBytes % bootSector.getBytesPerCluster() != 0 || totalBytes == 0) {
			// add dummy entry filled with zeros to mark end of entries
			buffer.put(new byte[32]);
		}

		buffer.flip();
		chain.write(0, buffer);
	}

	/**
	 * 
	 * @return True if this directory is the root directory.
	 */
    @Override
	public boolean isRoot() {
		return entry == null;
	}

	/**
	 * This method returns the volume label which can be stored in the root
	 * directory of a FAT32 file system.
	 * 
	 * @return The volume label.
	 */
	/* package */String getVolumeLabel() {
		return volumeLabel;
	}

	@Override
	public FatFile createFile(String name) throws LibAumsException {
		if (lfnMap.containsKey(name.toLowerCase(Locale.getDefault())))
			throw LibAumsException.createWithIOException("Item already exists!");

		init(); // initialise the directory before creating files

		ShortName shortName = ShortNameGenerator.generateShortName(name, shortNameMap.keySet());

		FatLfnDirectoryEntry entry = FatLfnDirectoryEntry.createNew(name, shortName);
		// alloc completely new chain
		long newStartCluster = fat.alloc(new Long[0], 1)[0];
		entry.setStartCluster(newStartCluster);

		log.debug( "adding entry: " + entry + " with short name: " + shortName);
		addEntry(entry, entry.getActualEntry());
		// write changes immediately to disk
		write();

		return FatFile.create(entry, blockDevice, fat, bootSector, this);
	}

	@Override
	public FatDirectory createDirectory(String name) throws LibAumsException {
		if (lfnMap.containsKey(name.toLowerCase(Locale.getDefault())))
			throw LibAumsException.createWithIOException("Item already exists!");

		init(); // initialise the directory before creating files

		ShortName shortName = ShortNameGenerator.generateShortName(name, shortNameMap.keySet());

		FatLfnDirectoryEntry entry = FatLfnDirectoryEntry.createNew(name, shortName);
		entry.setDirectory();
		// alloc completely new chain
		long newStartCluster = fat.alloc(new Long[0], 1)[0];
		entry.setStartCluster(newStartCluster);

		log.debug( "adding entry: " + entry + " with short name: " + shortName);
		addEntry(entry, entry.getActualEntry());
		// write changes immediately to disk
		write();

		FatDirectory result = FatDirectory.create(entry, blockDevice, fat, bootSector, this);

		result.entries = new ArrayList<FatLfnDirectoryEntry>(); // initialise entries before adding sub-directories

		// first create the dot entry which points to the dir just created
		FatLfnDirectoryEntry dotEntry = FatLfnDirectoryEntry
				.createNew(null, new ShortName(".", ""));
		dotEntry.setDirectory();
		dotEntry.setStartCluster(newStartCluster);
		FatLfnDirectoryEntry.copyDateTime(entry, dotEntry);
		result.addEntry(dotEntry, dotEntry.getActualEntry());

		// Second the dotdot entry which points to the parent directory (this)
		// if parent is the root dir then set start cluster to zero
		FatLfnDirectoryEntry dotDotEntry = FatLfnDirectoryEntry.createNew(null, new ShortName("..",
				""));
		dotDotEntry.setDirectory();
		dotDotEntry.setStartCluster(isRoot() ? 0 : entry.getStartCluster());
		FatLfnDirectoryEntry.copyDateTime(entry, dotDotEntry);
		result.addEntry(dotDotEntry, dotDotEntry.getActualEntry());

		// write changes immediately to disk
		result.write();

		return result;
	}

	@Override
	public void setLength(long newLength) {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public long getLength() {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public UsbFile search(String path) throws LibAumsException {
        log.debug( "search file: " + path);
        init();

        int index = path.indexOf(UsbFile.separator);

        if(index < 0) {
            log.debug( "search entry: " + path);

            FatLfnDirectoryEntry entry = findEntry(path);
            if(entry != null) {
                if(entry.isDirectory()) {
                    return FatDirectory.create(entry, blockDevice, fat, bootSector, this);
                } else {
                    return FatFile.create(entry, blockDevice, fat, bootSector, this);
                }
            }
        } else {
            String subPath = path.substring(index + 1);
            String dirName = path.substring(0, index);
            log.debug( "search recursively " + subPath + " in " + dirName);

            for(FatLfnDirectoryEntry entry : entries) {
                if(entry.isDirectory() && entry.getName().equals(dirName)) {
                    log.debug( "found " + dirName);
                    FatDirectory dir = FatDirectory.create(entry, blockDevice, fat, bootSector, this);
                    return dir.search(subPath);
                }
            }
        }

        log.debug( "not found " + path);

        return null;
	}

    private FatLfnDirectoryEntry findEntry(String name) {
        for(FatLfnDirectoryEntry entry : entries) {
            if(entry.getName().equals(name)) {
                return entry;
            }
        }

        return null;
    }

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public String getName() {
		return entry != null ? entry.getName() : "";
	}

	@Override
	public void setName(String newName) throws LibAumsException {
		if (isRoot())
			throw new IllegalStateException("Cannot rename root dir!");
		parent.renameEntry(entry, newName);
	}

	@Override
	public long createdAt() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getCreatedDateTime();
	}

	@Override
	public long lastModified() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getLastModifiedDateTime();
	}

	@Override
	public long lastAccessed() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getLastAccessedDateTime();
	}

	@Override
	public UsbFile getParent() {
		return parent;
	}

	@Override
	public String[] list() throws LibAumsException {
		init();
        List<String> list = new ArrayList<String>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			String name = entries.get(i).getName();
			if (!name.equals(".") && !name.equals("..")) {
				list.add(name);
			}
		}

        String[] array = new String[list.size()];
        array = list.toArray(array);

        return array;
	}

	@Override
	public UsbFile[] listFiles() throws LibAumsException {
        init();
        List<UsbFile> list = new ArrayList<UsbFile>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            FatLfnDirectoryEntry entry = entries.get(i);
            String name = entry.getName();
            if (name.equals(".") || name.equals(".."))
                continue;

            if (entry.isDirectory()) {
                list.add(FatDirectory.create(entry, blockDevice, fat, bootSector, this));
            } else {
                list.add(FatFile.create(entry, blockDevice, fat, bootSector, this));
            }
        }

        UsbFile[] array = new UsbFile[list.size()];
        array = list.toArray(array);

        return array;
	}

	@Override
	public void read(long offset, ByteBuffer destination) throws LibAumsException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void write(long offset, ByteBuffer source) throws LibAumsException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void flush() throws LibAumsException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void close() throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void moveTo(UsbFile destination) throws LibAumsException {
		if (isRoot())
			throw new IllegalStateException("cannot move root dir!");

		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or
		// partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw LibAumsException.createWithIOException("item already exists in destination!");

        init();
		destinationDir.init();

		// now the actual magic happens!
		parent.removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		parent.write();
		destinationDir.write();
		parent = destinationDir;
	}

	/**
	 * This method moves an long filename entry currently stored in THIS
	 * directory to the destination which also must be a directory.
	 * <p>
	 * Used by {@link FatFile} to move itself to another directory.
	 * 
	 * @param entry
	 *            The entry which shall be moved.
	 * @param destination
	 *            The destination directory.
	 * @throws IOException
	 *             If writing fails or the item already exists in the
	 *             destination directory.
	 * @throws IllegalStateException
	 *             If the destination is not a directory or destination is on a
	 *             different file system.
	 */
	/* package */void move(FatLfnDirectoryEntry entry, UsbFile destination) throws LibAumsException {
		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or
		// partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw LibAumsException.createWithIOException("item already exists in destination!");

		init();
		destinationDir.init();

		// now the actual magic happens!
		removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		write();
		destinationDir.write();
	}

	@Override
	public void delete() throws LibAumsException {
		if (isRoot())
			throw new IllegalStateException("Root dir cannot be deleted!");

		init();
		UsbFile[] subElements = listFiles();

		for (UsbFile file : subElements) {
			file.delete();
		}

		parent.removeEntry(entry);
		parent.write();
		chain.setLength(0);
	}
}
