package org.codec.biojavaencoder;




import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.codec.arraycompressors.FindDeltas;
import org.codec.arraycompressors.IntArrayCompressor;
import org.codec.arraycompressors.RunLengthEncode;
import org.codec.arraycompressors.RunLengthEncodeString;
import org.codec.arraycompressors.StringArrayCompressor;


import org.msgpack.jackson.dataformat.MessagePackFactory;
import com.fasterxml.jackson.core.JsonProcessingException;


import org.codec.biocompressors.BioCompressor;
import org.codec.biocompressors.CompressDoubles;

import org.codec.dataholders.BioDataStruct;
import org.codec.dataholders.CalphaBean;
import org.codec.dataholders.CalphaDistBean;
import org.codec.dataholders.CoreSingleStructure;

import org.codec.dataholders.MmtfBean;
import org.codec.dataholders.NoFloatDataStruct;
import org.codec.dataholders.NoFloatDataStructBean;
import org.codec.gitversion.GetRepoState;
import org.codec.dataholders.HeaderBean;


/**
 * This class finds an mmCIF file and saves it as a csv file 
 * 
 * @author Anthony Bradley
 */
public class EncoderUtils {
	
	private GetRepoState grs = new GetRepoState();
	private BioCompressor doublesToInts = new CompressDoubles();
	private IntArrayCompressor deltaComp = new FindDeltas();
	private IntArrayCompressor runLenghtComp = new RunLengthEncode();

	
	/**
	 * Function to take a list of integers (as List<Integer>)
	 * @param values
	 * @return
	 * @throws IOException
	 */
	private byte[] integersToBytes(List<Integer> values) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		for(Integer i: values)
		{
			dos.writeInt(i);
		}
		return baos.toByteArray();
	}


	/**
	 * Function to get a messagepack from a bean
	 * @param bioBean
	 * @return
	 * @throws JsonProcessingException
	 */
	public byte[] getMessagePack(Object bioBean) throws JsonProcessingException{
		com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper(new MessagePackFactory());
		byte[] inBuf = objectMapper.writeValueAsBytes(bioBean);
		return inBuf;
	}


	/**
	 * Function to compress the biological and header data into a combined data structure
	 * @param inStruct
	 * @param inHeader
	 * @return the byte array for the compressed data
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws IOException
	 */
	public byte[] compressMainData(BioDataStruct inStruct, HeaderBean inHeader) throws IllegalAccessException, InvocationTargetException, IOException {
		EncoderUtils cm = new EncoderUtils();
		// Compress the protein
		CoreSingleStructure bdh = compressHadoopStruct(inStruct);
		// NOW SET UP THE 
		MmtfBean thisDistBeanTot = new MmtfBean();
		NoFloatDataStructBean bioBean = (NoFloatDataStructBean) bdh.findDataAsBean();
		// Copy the shared properties across
		// Copy this across
		thisDistBeanTot.setInsCodeList(bioBean.get_atom_site_pdbx_PDB_ins_code());
		// Now get this list
		thisDistBeanTot.setBondAtomList(cm.integersToBytes(inStruct.getInterGroupBondInds()));
		thisDistBeanTot.setBondOrderList(cm.integersToSmallBytes(inStruct.getInterGroupBondOrders()));
		// Now get these from the headers
		thisDistBeanTot.setChainsPerModel(inHeader.getChainsPerModel());
		thisDistBeanTot.setGroupsPerChain(inHeader.getGroupsPerChain());
		thisDistBeanTot.setChainList(inHeader.getChainList());
		thisDistBeanTot.setNumAtoms(inHeader.getNumAtoms());
		thisDistBeanTot.setNumBonds(inHeader.getNumBonds());
		// Now get the Xtalographic info from this header
		thisDistBeanTot.setSpaceGroup(inHeader.getSpaceGroup());
		thisDistBeanTot.setUnitCell(inHeader.getUnitCell());
		thisDistBeanTot.setBioAssembly(inHeader.getBioAssembly());
		// Now set this extra header information
		thisDistBeanTot.setTitle(inHeader.getTitle());
		// Now add the byte arrays to the bean
		addByteArrs(thisDistBeanTot, bioBean);
		// Now set the version
		thisDistBeanTot.setMmtfProducer("RCSB-PDB Generator---version: "+grs.getCurrentVersion());
		// Now package as a MPF
		byte[] inBuf = getMessagePack(thisDistBeanTot);
		// NO GZIP YET
		return inBuf;
	}

	/**
	 * Function to add the required bytearrays to an mmtfbean
	 * @param thisDistBeanTot
	 * @param bioBean
	 * @throws IOException
	 */
	private void addByteArrs(MmtfBean thisDistBeanTot, NoFloatDataStructBean bioBean) throws IOException {
		EncoderUtils cm = new EncoderUtils();
		// X,Y and Z and Bfactors - set these arrays
		List<byte[]> retArr = getBigAndLittle(bioBean.get_atom_site_Cartn_xInt());
		thisDistBeanTot.setxCoordBig(retArr.get(0));
		thisDistBeanTot.setxCoordSmall(retArr.get(1));
		retArr = getBigAndLittle(bioBean.get_atom_site_Cartn_yInt());
		thisDistBeanTot.setyCoordBig(retArr.get(0));
		thisDistBeanTot.setyCoordSmall(retArr.get(1));
		retArr = getBigAndLittle(bioBean.get_atom_site_Cartn_zInt());
		thisDistBeanTot.setzCoordBig(retArr.get(0));
		thisDistBeanTot.setzCoordSmall(retArr.get(1));
		retArr = getBigAndLittle(bioBean.get_atom_site_B_iso_or_equivInt());
		thisDistBeanTot.setbFactorBig(retArr.get(0));
		thisDistBeanTot.setbFactorSmall(retArr.get(1));
		// Now the occupancy
		thisDistBeanTot.setOccList(cm.integersToBytes(bioBean.get_atom_site_occupancyInt()));
		// System.out.println(Collections.max(bioBean.getResOrder()));
		thisDistBeanTot.setGroupTypeList((cm.integersToBytes(bioBean.getResOrder())));
		thisDistBeanTot.setAtomIdList(cm.integersToBytes(bioBean.get_atom_site_id()));

		// Now the secondary structure
		thisDistBeanTot.setSecStructList(cm.integersToSmallBytes(bioBean.getSecStruct()));

		// Now this ectra information
		//		thisDistBeanTot.set_atom_site_label_alt_id(cm.charsToBytes(bioBean.get_atom_site_label_alt_id()));
		thisDistBeanTot.setGroupNumList(cm.integersToBytes(bioBean.get_atom_site_auth_seq_id()));
	}


	/**
	 * Function to write a list of integers to 1 byte ints
	 * @param values
	 * @return
	 * @throws IOException
	 */
	private byte[] integersToSmallBytes(List<Integer> values) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		for(int i: values)
		{
			dos.writeByte(i);
		}
		return baos.toByteArray();
	}

	/**
	 * Function to split an array into small and bgi integers
	 * @param inArr
	 * @return
	 * @throws IOException
	 */
	private List<byte[]> getBigAndLittle(List<Integer> inArr) throws IOException{
		List<byte[]>outArr = new ArrayList<byte[]>();
		int counter = 0;
		ByteArrayOutputStream littleOS = new ByteArrayOutputStream();
		DataOutputStream littleDOS = new DataOutputStream(littleOS);
		ByteArrayOutputStream bigOS = new ByteArrayOutputStream();
		DataOutputStream bigDOS = new DataOutputStream(bigOS);
		for(int i=0;i<inArr.size();i++){
			if(i==0){
				// First number goes in big list
				bigDOS.writeInt(inArr.get(i));
			}
			else if(Math.abs(inArr.get(i))>30000){
				// Counter added to the big list
				bigDOS.writeInt(counter);
				// Big number added to big list
				bigDOS.writeInt(inArr.get(i));
				// Counter set to zero
				counter = 0;
			}
			else{
				// Little number added to little list
				littleDOS.writeShort(inArr.get(i));
				// Add to the counter
				counter+=1;
			}
		}
		// Finally add the counter to the big list 
		bigDOS.writeInt(counter);

		outArr.add(bigOS.toByteArray());
		outArr.add(littleOS.toByteArray());
		return outArr;
	}

	/**
	 * Utility function to gzip compress a byte[]
	 * @param content
	 * @return
	 */
	public byte[] gzipCompress(byte[] content){
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try{
			GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
			gzipOutputStream.write(content);
			gzipOutputStream.close();
		} catch(IOException e){
			throw new RuntimeException(e);
		}
		System.out.printf("Compression %f\n", (1.0f * content.length/byteArrayOutputStream.size()));
		return byteArrayOutputStream.toByteArray();
	}

	/**
	 * Function to compress the input biological data
	 * @param bdh
	 * @return
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	public CoreSingleStructure compressHadoopStruct(BioDataStruct bdh) throws IllegalAccessException, InvocationTargetException{

		CoreSingleStructure outStruct = doublesToInts.compresStructure(bdh);

		// Get the input structure
		NoFloatDataStruct inStruct =  (NoFloatDataStruct) outStruct;
		ArrayList<Integer> cartnX = (ArrayList<Integer>) inStruct.get_atom_site_Cartn_xInt();
		ArrayList<Integer> cartnY = (ArrayList<Integer>) inStruct.get_atom_site_Cartn_yInt();
		ArrayList<Integer> cartnZ = (ArrayList<Integer>) inStruct.get_atom_site_Cartn_zInt();

		// Get the number of models
		inStruct.set_atom_site_Cartn_xInt(deltaComp.compressIntArray(cartnX));
		inStruct.set_atom_site_Cartn_yInt(deltaComp.compressIntArray(cartnY));
		inStruct.set_atom_site_Cartn_zInt(deltaComp.compressIntArray(cartnZ));		
		//		// Now the occupancy and BFACTOR -> VERY SMALL GAIN
		inStruct.set_atom_site_B_iso_or_equivInt(deltaComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_B_iso_or_equivInt()));
		//		inStruct.set_atom_site_B_iso_or_equivInt(intArrComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_B_iso_or_equivInt()));
		// SMALL GAIN
		inStruct.set_atom_site_occupancyInt(runLenghtComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_occupancyInt()));
		// Now the sequential numbers - huge gain - new order of good compressors
		// Now runlength encode the residue order
		inStruct.setResOrder(inStruct.getResOrder());
		// THESE ONES CAN BE RUN LENGTH ON DELTA

		// Check for negative counters
		inStruct.set_atom_site_auth_seq_id(runLenghtComp.compressIntArray(deltaComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_auth_seq_id())));
		inStruct.set_atom_site_label_entity_poly_seq_num(runLenghtComp.compressIntArray(deltaComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_label_entity_poly_seq_num())));
		inStruct.set_atom_site_id(runLenghtComp.compressIntArray(deltaComp.compressIntArray((ArrayList<Integer>) inStruct.get_atom_site_id())));
		//// NOW THE STRINGS  - small gain
		StringArrayCompressor stringRunEncode = new RunLengthEncodeString();
		inStruct.set_atom_site_label_alt_id(stringRunEncode.compressStringArray((ArrayList<String>) inStruct.get_atom_site_label_alt_id()));
		//inStruct.set_atom_site_label_entity_id(stringRunEncode.compressStringArray((ArrayList<String>) inStruct.get_atom_site_label_entity_id()));
		inStruct.set_atom_site_pdbx_PDB_ins_code(stringRunEncode.compressStringArray((ArrayList<String>) inStruct.get_atom_site_pdbx_PDB_ins_code()));
		return inStruct;
	}

	/**
	 * 
	 * @param calphaStruct
	 * @param inHeader
	 * @return
	 * @throws IOException
	 */
	public CalphaDistBean compCAlpha(CalphaBean calphaStruct, HeaderBean inHeader) throws IOException {
		EncoderUtils cm = new  EncoderUtils();
		// Create the object to leave
		CalphaDistBean calphaOut = new CalphaDistBean();
		calphaOut.setGroupsPerChain(calphaStruct.getGroupsPerChain());
		// Set this header info
		calphaOut.setChainsPerModel(inHeader.getChainsPerModel());
		calphaOut.setGroupsPerChain(inHeader.getGroupsPerChain());
		calphaOut.setChainList(inHeader.getChainList());
		calphaOut.setNumAtoms(calphaStruct.getNumAtoms());
		// Write the secondary stucture out
		calphaOut.setSecStruct(cm.integersToSmallBytes(calphaStruct.getSecStruct()));
		calphaOut.setGroupMap(calphaStruct.getGroupMap());
		calphaOut.setResOrder(cm.integersToBytes(calphaStruct.getResOrder()));
		// Get the input structure
		ArrayList<Integer> cartnX = (ArrayList<Integer>) calphaStruct.getCartn_x();
		ArrayList<Integer> cartnY = (ArrayList<Integer>) calphaStruct.getCartn_y();
		ArrayList<Integer> cartnZ = (ArrayList<Integer>) calphaStruct.getCartn_z();
		// Now add the X coords
		List<byte[]> bigAndLittleX = getBigAndLittle(deltaComp.compressIntArray(cartnX));
		calphaOut.setCartn_x_big(bigAndLittleX.get(0));
		calphaOut.setCartn_x_small(bigAndLittleX.get(1));
		//  No add they Y coords
		List<byte[]> bigAndLittleY = getBigAndLittle(deltaComp.compressIntArray(cartnY));
		calphaOut.setCartn_y_big(bigAndLittleY.get(0));
		calphaOut.setCartn_y_small(bigAndLittleY.get(1));
		// Now add the Z coords
		List<byte[]> bigAndLittleZ = getBigAndLittle(deltaComp.compressIntArray(cartnZ));
		calphaOut.setCartn_z_big(bigAndLittleZ.get(0));
		calphaOut.setCartn_z_small(bigAndLittleZ.get(1));	
		// THESE ONES CAN BE RUN LENGTH ON DELTA
		calphaOut.set_atom_site_auth_seq_id(cm.integersToBytes(runLenghtComp.compressIntArray(deltaComp.compressIntArray((ArrayList<Integer>) calphaStruct.get_atom_site_auth_seq_id()))));
		calphaOut.set_atom_site_label_entity_poly_seq_num(cm.integersToBytes(runLenghtComp.compressIntArray(deltaComp.compressIntArray((ArrayList<Integer>) calphaStruct.get_atom_site_label_entity_poly_seq_num()))));			
		return calphaOut;
	}

}
