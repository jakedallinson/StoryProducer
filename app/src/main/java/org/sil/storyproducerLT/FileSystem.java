package org.sil.storyproducerLT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by hannahbrown on 9/27/15.
 */
class FileSystem {
    private static String language = "English";

    //Paths to template directories from language and story name
    private static Map<String, Map<String, String>> storyPaths;

    private static final FilenameFilter directoryFilter = new FilenameFilter() {
        @Override
        public boolean accept(File current, String name) {
            return new File(current, name).isDirectory();
        }
    };

    public static void init() {
        loadStories();
    }

    //Populate storyPaths from files in system
    public static void loadStories() {
        //Reset storyPaths
        storyPaths = new HashMap<>();

        File[] storeDirs = getStorageDirs();
        for(int storeIndex = 0; storeIndex < storeDirs.length; storeIndex++) {
            File sDir = storeDirs[storeIndex];

            File[] langDirs = getLanguageDirs(sDir);
            for(int langIndex = 0; langIndex < langDirs.length; langIndex++) {
                File lDir = langDirs[langIndex];
                String lang = lDir.getName();

                if(!storyPaths.containsKey(lang)) {
                    storyPaths.put(lang, new HashMap<String, String>());
                }
                Map<String, String> storyMap = storyPaths.get(lang);

                File[] storyDirs = getStoryDirs(lDir);
                for(int storyIndex = 0; storyIndex < storyDirs.length; storyIndex++) {
                    File storyDir = storyDirs[storyIndex];
                    String storyName = storyDir.getName();
                    String storyPath = storyDir.getPath();
                    storyMap.put(storyName, storyPath);
                }
            }
        }
    }

    public static void changeLanguage(String lang) {
        language = lang;
    }

    private static File[] getStorageDirs() {
        return Main.getAppContext().getExternalFilesDirs(null);
    }
    private static File[] getLanguageDirs(File storageDir) {
        return storageDir.listFiles(directoryFilter);
    }
    private static File[] getStoryDirs(File langDir) {
        return langDir.listFiles(directoryFilter);
    }

    public static String getStoryPath(String story) {
        Map<String, String> storyMap = storyPaths.get(language);
        if(storyMap != null) {
            return storyMap.get(story);
        }
        return null;
    }

    public static String[] getStoryNames() {
        Map<String, String> storyMap = storyPaths.get(language);
        if (storyMap != null) {
            Set<String> keys = storyMap.keySet();
            return keys.toArray(new String[keys.size()]);
        }
        return new String[0];
    }

    public static Bitmap getImage(String story, int number, int sampleSize) {
        String path = getStoryPath(story);
        File f = new File(path);
        File file[] = f.listFiles();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;

        for (int i=0; i < file.length; i++) {
                if (file[i].getName().equals(number + ".jpg")) {
                    return BitmapFactory.decodeFile(path + "/" + file[i].getName(), options);
                }
        }
        return null;
    }
    public static Bitmap getAudio(String story, int number) {
        String path = getStoryPath(story);
        File f = new File(path);
        File file[] = f.listFiles();

        for (int i=0; i < file.length; i++) {
            if (file[i].getName().equals(number + ".wav")) {
                return BitmapFactory.decodeFile(path + "/" + file[i].getName());
            }
        }
        return null;
    }

    public static int getImageAmount(String storyName){
        String path = getStoryPath(storyName);
        File f = new File(path);
        File file[] = f.listFiles();
        int count = 0;
        for(int i=0; i<file.length; i++) {
            if (!file[i].isHidden() && file[i].getName().matches("\\d+\\.jpg")) {
                count++;
            }
        }
        return count;
    }

    private static String[] content;
    public static void loadSlideContent(String storyName, int slideNum){
        File file = new File(getStoryPath(storyName), (slideNum + ".txt"));
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }


        String text1 = text.toString();
        byte[] temp = text1.getBytes();
        for(int i = 0; i < temp.length - 2; i++) {
            //Swap out curly apostrophe with ASCII single quote
            if(temp[i] == -17 && temp[i+1] == -65 && temp[i+2] == -67) {
                text = text.replace(i, i+1, "'");
                text1 = text.toString();
                temp = text1.getBytes();
            }
        }
        content = text.toString().split(Pattern.quote("~"));
    }

    public static String getTitle(){
        if (content.length > 0 && content[0] != null) {
            return content[0];
        } else {
            return "";
        }
    }
    public static String getSubTitle(){
        if (content.length > 1 && content[1] != null) {
            return content[1];
        } else {
            return "";
        }
    }
    public static String getSlideVerse(){
        if (content.length > 2 && content[2] != null) {
            return content[2];
        } else {
            return "";
        }
    }
    public static String getSlideContent(){
        if (content.length > 3 && content[3] != null) {
            return content[3];
        } else {
            return "";
        }
    }

    public static String[] getLanguages() {
        return storyPaths.keySet().toArray(new String[storyPaths.size()]);
    }
}