package xapdoc.parser;

import java.io.*;
import java.util.*;

public class MenuTree {

    private static final boolean DEBUG_ENABLED = false;	
    private static final Collection<String> OLD_VERSIONS = Arrays.asList("97");
    private static String BASE_PATH;

    private static final String[] SHARED_DIRS = new String[] {
            "product_overview", "faq", "api_documentation", "release_notes", "howto", "videos", "sbp"};

    private final long startTime = System.currentTimeMillis();
    private int totalFolders;
    private int totalPages;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            warning("Incorrect number of arguments: " + args.length);
            System.exit(1);
        }
        // e.g. BASE_PATH = "/Users/xxxxx/Documents/xap-docs";
        BASE_PATH = args[0];
        info("Starting with base path " + BASE_PATH);

        final String contentPath = BASE_PATH + "/site/content/";
        MenuTree instance = new MenuTree();
        for (String dir : SHARED_DIRS)
            instance.processDir(new File(contentPath + dir));
        Collection<VersionContainer> xapVersions = getProductFolders(contentPath);
        for (VersionContainer vc : xapVersions) {
            if (OLD_VERSIONS.contains(vc.version)) {
                for (File folder : vc.files) {
                    instance.processDir(folder);
                }
            } else {
                instance.processVersion(vc);
            }
        }

        long duration = System.currentTimeMillis() - instance.startTime;
        info("Finished generating navbar (duration=" + duration + "ms" +
                ", folders=" + instance.totalFolders +
                ", pages=" + instance.totalPages + ")");
    }

    private void processVersion(VersionContainer vc) throws IOException {
		debug("processVersion(" + vc.version + ")");
        Map<String, Page> rootsMap = new HashMap<String, Page>();
        for (File folder : vc.files) {
            final Collection<Page> folderRoot = loadPages(folder, true);
            if (folderRoot.isEmpty())
                warning("No root for " + folder.getName());
            else if (folderRoot.size() != 1)
                warning("Ambiguous root for " + folder.getName());
            else {
                rootsMap.put(folder.getName(), folderRoot.iterator().next());
				debug("Processed " + folder.getName());
			}
        }
		File indexFile = new File(vc.path, "index.markdown");
		if (indexFile.exists()) 
            rootsMap.put("intro", new Page(indexFile, true));
        // Relocate java tutorial from root under java dev guide:
		relocate(rootsMap, "xap" + vc.version + "tut", "xap" + vc.version);
		relocate(rootsMap, "tut-java", "dev-java");
        // Relocate .NET tutorial from root under .NET dev guide:
		relocate(rootsMap, "xap" + vc.version + "nettut", "xap" + vc.version + "net");
		relocate(rootsMap, "tut-dotnet", "dev-dotnet");
        // Relocate security under admin:
		if (vc.version.equals("122"))
			relocate(rootsMap, "security", "admin");

        // Sort and generate roots:
        generateSidenav("xap" + vc.version, new TreeSet<Page>(rootsMap.values()));
    }
	
	private static void relocate(Map<String, Page> rootsMap, String sourceKey, String targetKey) {
		Page source = rootsMap.remove(sourceKey);
		if (source != null)
            rootsMap.get(targetKey).addChild(source);
	}

    private static Collection<VersionContainer> getProductFolders(String path) {
        Collection<VersionContainer> result = getProductFoldersOld(path);
        for (File versionDir : new File(path, "xap").listFiles()) {
            if (versionDir.isDirectory()) {
				VersionContainer vc = new VersionContainer(versionDir);
				result.add(vc);
				for (File contentDir : versionDir.listFiles()) {
					if (contentDir.isDirectory()) {
						vc.files.add(contentDir);						
					}
				}
			}				
        }

        return result;
    }

    private static Collection<VersionContainer> getProductFoldersOld(String path) {
        Collection<VersionContainer> result = new HashSet<VersionContainer>();
        for (File file : new File(path).listFiles()) {
            if (file.isDirectory() && file.getName().startsWith("xap") && !file.getName().equals("xap") ) {
				getOrCreateVersionContainer(result, file).files.add(file);
			}
        }

        return result;
    }
	
	private static VersionContainer getOrCreateVersionContainer(Collection<VersionContainer> containers, File path) {
		for (VersionContainer vc : containers) {
			if (vc.path.equals(path))
				return vc;
		}
		VersionContainer vc = new VersionContainer(path, extractVersion(path.getName()));
		containers.add(vc);
		return vc;
	}
	
    private static String extractVersion(String name) {
        String result = "";
        for (int i="xap".length() ; i < name.length() && Character.isDigit(name.charAt(i)) ; i++)
            result += name.charAt(i);
		
        return result;
    }

    private void processDir(File folder) throws IOException {
        generateSidenav(folder.getName(), loadPages(folder, false));
    }

    private void generateSidenav(String suffix, Collection<Page> roots) throws IOException {
        // write the html to the file system
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(BASE_PATH + "/site/themes/hugo-bootswatch/layouts/partials/sidenav-" + suffix + ".html", "UTF-8");
            for (Page root : roots) {
                printPage(writer, root);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private Collection<Page> loadPages(File folder, boolean groupingMode) throws IOException {
        debug("Processing dir : " + folder.getName());
        final Collection<Page> roots = new TreeSet<Page>();
        final Map<String, Page> pages = new HashMap<String,Page>();
        for (File file : folder.listFiles()) {
            // make sure we only process markdown files
            if (file.isFile() && file.getName().endsWith(".markdown")) {
                Page p = new Page(file, groupingMode);
                pages.put(p.getId(), p);
                if (p.getParent().isEmpty())
                    roots.add(p);
            }
        }

        totalFolders++;
        totalPages += pages.size();

        buildTree(pages);
        return roots;
    }

    private static void buildTree(Map<String, Page> pages) {
        // now lets order them according to the weight
        for (Page p : pages.values()) {
            if (p.getWeight() == null) {
                if (!p.isIndex())
                    warning(p.getSource() + "  has no weight");
            } else {
                if (!p.getParent().isEmpty()) {
                    Page parent = pages.get(p.getParent());
                    if (parent != null)
                        parent.addChild(p);
                    else
                        warning(p.getSource() + " - invalid parent [" + p.getParent() + "]");
                }
            }
        }
    }

    private static void printPage(PrintWriter writer, Page page) {
        String link = "<a href='/" + page.getHref() + "'>" + page.getTitle() + "</a>";
        if (page.getChildren().size() == 0) {
            writer.println("<li>" + link + "</li>");
        } else {
            writer.println("<li class='expandable'><div class='hitarea expandable-hitarea'></div>" + link);
            writer.println("<ul style='display: none'>");
            for (Page child : page.getChildren())
                printPage(writer, child);
            writer.println("</ul>");
            writer.println("</li>");
        }
    }

    private static void info(String message)  {
        System.out.println(message);
    }

    private static void warning(String message)  {
        System.out.println("WARNING: " + message);
    }

    private static void debug(String message)  {
        if (DEBUG_ENABLED)
            System.out.println("DEBUG: " + message);
    }
    private static class VersionContainer {
		private final File path;
		private final String version;
		private final Collection<File> files = new ArrayList<File>();
		
		public VersionContainer(File path) {
			this(path, path.getName().replace(".", ""));
		}
		
		public VersionContainer(File path, String version) {
			this.path = path;
			this.version = version;
		}
	}
}
