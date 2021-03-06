/**
 * 
 */
package osmgpxtool.filter.writer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import osmgpxtool.filter.GpxFilter;
import osmgpxtool.filter.gpx.schema10.Gpx;
import osmgpxtool.filter.gpx.schema10.Gpx.Trk;
import osmgpxtool.filter.metadata.schema.GpxFiles;
import osmgpxtool.filter.metadata.schema.GpxFiles.GpxFile;

public class DumpWriter implements Writer {
	static Logger LOGGER = LoggerFactory.getLogger(DumpWriter.class);

	private String outFile;
	private FileOutputStream fOut;
	private BufferedOutputStream bOut;
	private XZCompressorOutputStream xOut;
	private TarArchiveOutputStream tarOut;
	private JAXBContext jaxbContext;
	private Marshaller jaxbMarshaller;
	private ByteArrayOutputStream os;
	private GpxFiles metadataFile;
	private String metafilename;
	private GpxFilter filter;

	/**
	 * Writer for gps traces to tar.xz file archive.
	 * 
	 * @param outFile
	 * @param metadataFilename
	 */
	public DumpWriter(GpxFilter filter, String outFile) {
		super();
		this.outFile = outFile;
		this.filter = filter;
	}

	@Override
	public void init() {
		// init tar outputstream
		try {
			fOut = new FileOutputStream(new File(outFile));
			bOut = new BufferedOutputStream(fOut);
			xOut = new XZCompressorOutputStream(bOut);
			tarOut = new TarArchiveOutputStream(xOut);
		} catch (IOException e) {
			LOGGER.error("could not find output path.");
			e.printStackTrace();
		}

		// init Marshaller

		try {
			jaxbContext = JAXBContext.newInstance(Gpx.class);
			jaxbMarshaller = jaxbContext.createMarshaller();
			// output pretty printed
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		} catch (JAXBException e) {
			e.printStackTrace();
		}

		// init metadata
		metadataFile = new GpxFiles();

	}

	@Override
	public void write(Gpx gpx, String filename, GpxFile metadata) {
		if (metadata == null) {
			LOGGER.warn("Skipped because of missing metadata: " + filename);
		} else {
			// if all tracks of gpx file pass the filter, write gpx file.
			boolean passesFilter = false;
			for (Trk trk : gpx.getTrk()) {
				if ( filter.check(trk)) {
					passesFilter = true;
				}
			}
			if (passesFilter) {
				metadataFile.getGpxFile().add(metadata);
				try {
					os = new ByteArrayOutputStream();
					jaxbMarshaller.marshal(gpx, os);
					TarArchiveEntry tarEntry = new TarArchiveEntry(filename, true);
					tarEntry.setSize(os.size());
					tarOut.putArchiveEntry(tarEntry);
					tarOut.write(os.toByteArray());
					tarOut.closeArchiveEntry();
				} catch (JAXBException | IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void close() {
		writeMetadataFile();
		try {
			if (tarOut != null)		
				tarOut.close();
			if (fOut != null)
				fOut.close();
			if (bOut != null)
				bOut.close();
			if (xOut != null)
				xOut.close();
		} catch (IOException e) {
			LOGGER.error("Could not close Outputstream.");
			e.printStackTrace();
		}
	}

	private void writeMetadataFile() {
		try {
			JAXBContext jaxbContextMeta = JAXBContext.newInstance(GpxFiles.class);
			Marshaller jaxbMarshallerMeta = jaxbContextMeta.createMarshaller();
			// output pretty printed
			jaxbMarshallerMeta.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			os = new ByteArrayOutputStream();
			jaxbMarshallerMeta.marshal(metadataFile, os);
			TarArchiveEntry tarEntry = new TarArchiveEntry(metafilename, true);
			tarEntry.setSize(os.size());
			tarOut.putArchiveEntry(tarEntry);
			tarOut.write(os.toByteArray());
			tarOut.closeArchiveEntry();
		} catch (JAXBException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public GpxFilter getFilter() {

		return filter;
	}

	@Override
	public void setMetadataFilename(String filename) {
		this.metafilename = filename;
	}
}
