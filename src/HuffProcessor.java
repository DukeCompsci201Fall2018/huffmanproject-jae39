import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
	}
	
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;		
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));

		
	}

	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root == null) return;
		if (root.myValue == 0) {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		helper(root, "", encodings);
		return encodings;
	}

	private void helper(HuffNode root, String path, String[] encodings) {
		// if you get to a leaf, store the path in the right bin in encodings
		if (root.myLeft == null && root.myRight == null) {
			encodings[root.myValue] = path;
			return;
		}
		
		if (root.myLeft != null) helper(root.myLeft, path + "0", encodings);
		if (root.myRight != null) helper(root.myRight, path + "1", encodings);
	}

	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		// add each huffnode to pq
		for (int j = 0; j < counts.length; j++) {
			if (counts[j] == 0) continue;
			pq.add(new HuffNode(j, counts[j], null, null));
		}

		// while pq contains multiple nodes, combine smallest two
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight + right.myWeight, left, right);
			pq.add(t);
		}
		
		// return final product
		return pq.remove();
	}

	private int[] readForCounts(BitInputStream in) {
		int[] ret = new int[ALPH_SIZE + 1];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			ret[val] += 1;
		}
		ret[PSEUDO_EOF] = 1;
		return ret;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if (myDebugLevel > DEBUG_HIGH) System.out.println(bits);
		
		if (bits != HUFF_TREE) throw new HuffException("illegal header starts with " + bits);
		
		HuffNode root = readTreeHeader(in);
		if (myDebugLevel > DEBUG_HIGH) huffPrint(root);
		readCompressedBits(root, in, out);
		out.close();
		
	}

	private void huffPrint(HuffNode root) {
		if (root == null) return;
		System.out.println(root);
		huffPrint(root.myLeft);
		huffPrint(root.myRight);
	}

	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
			HuffNode current = root;
			while (true) {
				int bits = in.readBits(1);
				if (bits == -1) {
					throw new HuffException("bad input, no PSEUDO_EOF");
				}
				else {
					if (bits == 0) current = current.myLeft;
					else current = current.myRight;
					
					if (current.myLeft == null && current.myRight == null) {
						if (current.myValue == PSEUDO_EOF) {
							break;
						}
						else {
							if (myDebugLevel > DEBUG_HIGH) {
								System.out.print(current);
								System.out.println(current.myValue);
							}
							out.writeBits(BITS_PER_WORD, current.myValue);
							current = root;
						}
					}
				}
			}
			
		}
	
	
	/*
	 * turns an instream into a tree of huffnodes, returns first huffnode
	 * 
	 * Words (confirmed via debugging)
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
			int bit = in.readBits(1);
			if (bit == -1) {
				throw new HuffException("bad input");
			}
			if (bit == 0) {
				HuffNode left = readTreeHeader(in);
				HuffNode right = readTreeHeader(in);
				return new HuffNode(0, 0,left, right);
			}
			else {
				int val = in.readBits(BITS_PER_WORD  + 1);
				return new HuffNode(val, 0);			
			}
		
		}
}