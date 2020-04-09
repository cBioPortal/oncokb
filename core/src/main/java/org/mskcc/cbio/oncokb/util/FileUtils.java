/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.mskcc.cbio.oncokb.apiModels.download.FileExtension;
import org.mskcc.cbio.oncokb.apiModels.download.FileName;

/**
 *
 * @author jgao
 */
public class FileUtils {
    private FileUtils() {
        throw new AssertionError();
    }

    public static List<String> getFilesInFolder(final String pathToFolder, final String suffix) {
        File folder = new File(pathToFolder);

        String[] files = folder.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toUpperCase().endsWith(suffix.toUpperCase());
            }
        });

        List<String> ret = new ArrayList<String>();
        for (String file : files) {
            ret.add(pathToFolder + File.separator + file);
        }

        return ret;
    }

    /**
     * read local files and return content
     * @param pathToFile
     * @return
     * @throws IOException
     */
    public static String readLocal(String pathToFile) throws IOException {
        return readStream(new FileInputStream(pathToFile));
    }

    /**
     * return remote files and return content
     * @param urlToFile
     * @return
     * @throws IOException
     */
    public static String readRemote(String urlToFile) throws IOException {
        URL url = new URL(urlToFile);
        return readStream(url.openStream());
    }

    public static String readPublicOncoKBRemote(String urlToFile) throws IOException {
        URL myURL = new URL(urlToFile);
        HttpURLConnection myURLConnection = (HttpURLConnection)myURL.openConnection();
        myURLConnection.setRequestProperty ("Authorization", "Bearer " + PropertiesUtils.getProperties("public_oncokb.token"));
        myURLConnection.setRequestMethod("GET");
        return readStream(myURLConnection.getInputStream());
    }

    /**
     * read a stream and return content
     * @param is
     * @return
     * @throws IOException
     */
    public static String readStream(InputStream is) throws IOException {
        List<String> lines = readTrimedLinesStream(is);
        return StringUtils.join(lines, "\n");
    }

    public static List<String> readTrimedLinesStream(InputStream is) throws IOException {
        return readLinesStream(is, true);
    }

    /**
     * read a stream and return lines
     * @param is
     * @return
     * @throws IOException
     */
    public static List<String> readLinesStream(InputStream is, boolean trim) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));

        List<String> lines = new ArrayList<String>();
        String line;
        while ((line = in.readLine()) != null) {
            if (trim) {
                line = line.trim();
            }
            if (!line.isEmpty())
                lines.add(line);
        }
        in.close();

        return lines;
    }

    public static String getFileName(FileName fileName, FileExtension fileExtension) {
        return fileName.getName() + fileExtension.getExtension();
    }
}
