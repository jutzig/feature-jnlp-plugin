package de.jutzig.maven.eclipse.jnlp.plugin;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Goal which touches a timestamp file.
 *
 * @goal generate-jnlp
 * @phase package
 */
public class GenerateJNLPMojo
    extends AbstractMojo
{
    /**
     * Location of the file.
     *
     * @parameter expression= "target/site/features/${project.artifactId}_${project.version}.jar"
     * @required
     */
    private File featureJar;

    /**
     * the vendor as it appear in the JNLP
     *
     * @parameter expression= "${project.organization.name}"
     * @required
     */
    private String vendor;

    /**
     * the title as it appear in the JNLP
     *
     * @parameter expression= "${project.name}"
     * @required
     */
    private String title;

    /**
     * the vendor as it appear in the JNLP
     *
     * @parameter expression= "${project.url}"
     * @required
     */
    private String url;


    public void execute()
        throws MojoExecutionException
    {

        File jnlp = new File(featureJar.getParentFile(),
                             featureJar.getName().substring(0, featureJar.getName().length() - 3) + "jnlp");

        Document feature = parseFeature(featureJar);
        // NodeList plugins = feature.getElementsByTagName("plugin");
        Document jnlpDoc = createJNLP(feature);
        Writer out = null;
        try
        {

            TransformerFactory tFactory = TransformerFactory.newInstance();
            tFactory.setAttribute("indent-number", 3);

            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(jnlpDoc);
            out = new OutputStreamWriter(new FileOutputStream(jnlp));
            StreamResult result = new StreamResult(out);
            transformer.transform(source, result);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Error creating file " + jnlp, e);
        }
        catch (TransformerConfigurationException e)
        {
            throw new MojoExecutionException("Transformation Configuration Error", e);
        }
        catch (TransformerException e)
        {
            throw new MojoExecutionException("Transformation Error" + jnlp, e);
        }
        finally
        {
            if (out != null)
            {
                try
                {
                    out.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
    }


    private Document createJNLP(Document feature) throws MojoExecutionException
    {
        try
        {
            Element root = feature.getDocumentElement();
            Document jnlp = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element jnlpElement = jnlp.createElement("jnlp");
            jnlp.appendChild(jnlpElement);
            jnlpElement.setAttribute("spec", "1.0+");
            jnlpElement.setAttribute("codebase", url);

            Element information = jnlp.createElement("information");
            jnlpElement.appendChild(information);

            Element title = jnlp.createElement("title");
            title.setTextContent(this.title);
            information.appendChild(title);

            Element vendor = jnlp.createElement("vendor");
            vendor.setTextContent(this.vendor);
            information.appendChild(vendor);

            Element offline = jnlp.createElement("offline-allowed");
            information.appendChild(offline);

            Element security = jnlp.createElement("security");
            jnlpElement.appendChild(security);

            Element permissions = jnlp.createElement("all-permissions");
            security.appendChild(permissions);

            Element component = jnlp.createElement("component-desc");
            jnlpElement.appendChild(component);

            Element resources = jnlp.createElement("resources");
            jnlpElement.appendChild(resources);

            Element j2se = jnlp.createElement("j2se");
            resources.appendChild(j2se);
            j2se.setAttribute("version", "1.6+");

            NodeList plugins = root.getElementsByTagName("plugin");
            int size = plugins.getLength();
            for (int i = 0; i < size; i++)
            {
                Element plugin = (Element)plugins.item(i);
                String arch = plugin.getAttribute("arch");

                // if an arch is set, we must add several alternatives names for the same thing :-(
                if (arch.equals("x86_64"))
                {
                    createResourceElement(plugin, jnlp, jnlpElement, "x86_64");
                    createResourceElement(plugin, jnlp, jnlpElement, "amd64");

                }
                else if (arch.equals("x86"))
                {
                    createResourceElement(plugin, jnlp, jnlpElement, "x86");
                    createResourceElement(plugin, jnlp, jnlpElement, "i386");
                    createResourceElement(plugin, jnlp, jnlpElement, "i686");
                }
                else
                    createResourceElement(plugin, jnlp, jnlpElement, null);
            }

            return jnlp;

        }
        catch (DOMException e)
        {
            throw new MojoExecutionException("Error processing DOM",e);
        }
        catch (ParserConfigurationException e)
        {
            throw new MojoExecutionException("Parser Configuration Error",e);
        }
    }


    private void createResourceElement(Element plugin, Document jnlp, Node jnlpElement, String arch)
    {
        String version = plugin.getAttribute("version");
        if (version == null || version.length() == 0 || version.equals("0.0.0"))
            // this plugin is not included for some reason. We must skip
            return;
        Element resource = jnlp.createElement("resources");
        jnlpElement.appendChild(resource);
        Element jar = jnlp.createElement("jar");
        resource.appendChild(jar);
        jar.setAttribute("href",
                         "plugins/" + plugin.getAttribute("id") + "_" + plugin.getAttribute("version")
                                         + ".jar");

        String os = plugin.getAttribute("os");
        if (os.contains("win"))
            resource.setAttribute("os", "Windows");
        if (os.contains("mac"))
            resource.setAttribute("os", "Mac");
        if (os.contains("linux"))
            resource.setAttribute("os", "Linux");

        if(arch!=null)
            resource.setAttribute("arch", arch);

    }


    private Document parseFeature(File featureJar)
    {
        ZipInputStream in = null;
        try
        {
            in = new ZipInputStream(new FileInputStream(featureJar));
            ZipEntry entry = null;
            while ((entry = in.getNextEntry()) != null)
            {
                if (entry.getName().equals("feature.xml"))
                    break;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);

            Document document = factory.newDocumentBuilder().parse(in);
            return document;
        }
        catch (FileNotFoundException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (SAXException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ParserConfigurationException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally
        {
            if (in != null)
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
        return null;
    }
}
