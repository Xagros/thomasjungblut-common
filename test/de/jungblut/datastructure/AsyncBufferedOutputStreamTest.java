package de.jungblut.datastructure;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;

import junit.framework.TestCase;

import org.junit.Test;

public class AsyncBufferedOutputStreamTest extends TestCase {

  @Test
  public void testWrites() throws Exception {
    File tempFile = File.createTempFile("async_test", "tmp", new File("/tmp/"));
    tempFile.deleteOnExit();
    byte[] ones = new byte[32];
    Arrays.fill(ones, (byte) 1);
    byte[] zeros = new byte[32];
    // that should yield to 256mb
    int numItems = 4194304;
    try (AsyncBufferedOutputStream out = new AsyncBufferedOutputStream(
        new FileOutputStream(tempFile), 512 * 1024)) {

      // test write 32 bytes 1s, and 32 bytes 0s
      for (int i = 0; i < numItems; i++) {
        out.write(ones);
        out.write(zeros);
      }
    }

    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(
        tempFile))) {
      byte[] buf = new byte[32];
      for (int i = 0; i < numItems; i++) {
        int read = in.read(buf);
        assertEquals(32, read);
        for (int x = 0; x < 32; x++)
          assertEquals(1, buf[x]);
        read = in.read(buf);
        assertEquals(32, read);
        for (int x = 0; x < 32; x++)
          assertEquals(0, buf[x]);
      }
    }

  }

  @Test
  public void testRaceWrites() throws Exception {
    File tempFile = File.createTempFile("async_test_race_writes", "tmp",
        new File("/tmp/"));
    tempFile.deleteOnExit();
    byte[] ones = new byte[32];
    Arrays.fill(ones, (byte) 1);
    byte[] zeros = new byte[32];
    int numItems = 2500;
    @SuppressWarnings("resource")
    AsyncBufferedOutputStream out = new AsyncBufferedOutputStream(
        new FileOutputStream(tempFile), 512 * 1024);
    // test write 32 bytes 1s, and 32 bytes 0s for 1gb
    for (int i = 0; i < numItems; i++) {
      out.write(ones);
      out.write(zeros);
    }
    // we wait ~2s before all items are written asynchronously
    Thread.sleep(2000L);
    // now close would wait for an item to add infinitely
    out.close();

    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(
        tempFile))) {
      byte[] buf = new byte[32];
      for (int i = 0; i < numItems; i++) {
        int read = in.read(buf);
        assertEquals(32, read);
        for (int x = 0; x < 32; x++)
          assertEquals(1, buf[x]);
        read = in.read(buf);
        assertEquals(32, read);
        for (int x = 0; x < 32; x++)
          assertEquals(0, buf[x]);
      }
    }

  }
}
