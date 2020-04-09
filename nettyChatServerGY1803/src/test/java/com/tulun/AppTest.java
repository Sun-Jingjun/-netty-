package com.tulun;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }

    @Test
    public void test() throws IOException {
        File file = new File("e:\\a\\a.txt");
        FileInputStream fileInputStream = new FileInputStream(file);
        int read = fileInputStream.read();
        System.out.println((char) read);
    }
}
