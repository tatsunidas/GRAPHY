package com.vis.cdw.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

public class FileDelete {

	//debug
	public static void main(String[] args) {
		// https://stackoverflow.com/questions/31220350/how-can-we-list-all-files-and-folders-recursively
		File deleteRoot = new File("tmp/DICOM-CD-TEST");
//		new TestDelete().deleteAll(deleteRoot);//it is ok
		new FileDelete().deleteDir(deleteRoot);//also ok
		
		/*
		 * if subdir exists, can not delete dir. so, delete it recursively.
		 */		
//		FileUtils.deleteQuietly(root);

		// 同じ階層に複数のサブフォルダがあるとNG
//		try {
//			FileUtils.deleteDirectory(root);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public FileDelete() {}
	
	public void deleteAll(File deleteRoot) {
		Collection<File> filesAndDirs = FileUtils.listFilesAndDirs(deleteRoot, TrueFileFilter.INSTANCE,
				TrueFileFilter.INSTANCE);
		for (File f : filesAndDirs) {
			if (f.isFile()) {
				deleteFile(f);
			}
		}
		listFileAndDelete(deleteRoot);
	}
	
	/*
	 * これ単独では、全ファイルは消えるが、ディレクトリのスケルトンが残る
	 * いったん、ファイルを全削除してから、ディレクトリのスケルトンにして、削除する。
	 */
	static void listFileAndDelete(File root) {
		Path dir = root.toPath();
		try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
			walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			dir.toFile().delete();
		} catch (IOException ie) {
			System.out.println(ie);
		}
	}

	public void deleteDir(File file) {
		//delete files
		if (file.isFile()) {
			deleteFile(file);
		} else if (file.isDirectory()) {
			File[] contents = file.listFiles();
			if (contents != null) {
				for (File f : contents) {
					deleteDir(f);
				}
				deleteFile(file);
			} else {
				deleteFile(file);
			}
		}
	}

	private void deleteFile(File subdir) {
		/*
		 * got error Error プロセスはファイルにアクセスできません。別のプロセスが使用中です。
		 */
//		try {
//			// Error プロセスはファイルにアクセスできません。別のプロセスが使用中です。
//			java.nio.file.Files.delete(subdir.toPath());
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		// no error
		subdir.delete(); // TODO ?? to avoid symlink
	}
}
