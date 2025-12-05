import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class AsmVm {
    // Параметры VM
    static final int MEMORY_SIZE = 256;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            return;
        }
        String cmd = args[0];
        if ("assemble".equals(cmd)) {
            if (args.length != 3) { usage(); return; }
            assemble(Paths.get(args[1]), Paths.get(args[2]));
        } else if ("run".equals(cmd)) {
            if (args.length != 2 && args.length != 3) { usage(); return; }
            Path dumpPath = null;
            if (args.length == 3) {
                dumpPath = Paths.get(args[2]);
            }
            runProgram(Paths.get(args[1]), dumpPath);
        } else {
            usage();
        }
    }

    static void usage() {
        System.out.println("AsmVm — assembler + interpreter");
        System.out.println("Usage:");
        System.out.println("  java AsmVm assemble input.asm output.bin");
        System.out.println("  java AsmVm run output.bin");
        System.out.println("  java AsmVm run output.bin [dump.xml]");
    }

    // ---------- Assembler ----------
    static void assemble(Path in, Path out) throws IOException {
        List<String> lines = Files.readAllLines(in);
        List<byte[]> encoded = new ArrayList<>();
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith(";")) continue;
            // strip comments after ';'
            int cpos = s.indexOf(';');
            if (cpos >= 0) s = s.substring(0, cpos).trim();
            if (s.isEmpty()) continue;

            String[] tok = s.split("\\s+");
            String op = tok[0].toLowerCase(Locale.ROOT);
            try {
                long word = 0L;
                switch (op) {
                    case "load": {
                        if (tok.length != 3 && tok.length != 4) {
                            throw new IllegalArgumentException("load expects 2 args: load dest const");
                        }
                        // support both "load 1 10" and "load dest const"
                        int dest = Integer.parseInt(tok[1]);
                        int constant = Integer.parseInt(tok[2]);
                        // fields
                        putBitsCheck(word); // no-op helper visual
                        word = 0L;
                        word |= (long)(16 & 0x1F); // cmd code 16
                        word |= ((long)dest & 0xF) << 5; // bits 5..8
                        // const: 10 bits signed
                        long constField = toUnsigned(constant, 10);
                        word |= (constField & ((1L<<10)-1)) << 9; // bits 9..18
                        encoded.add(to7BytesLE(word));
                        break;
                    }
                    case "read": {
                        if (tok.length != 4) throw new IllegalArgumentException("read expects 3 args: read dest src offset");
                        int dest = Integer.parseInt(tok[1]);
                        int src = Integer.parseInt(tok[2]);
                        int offset = Integer.parseInt(tok[3]);
                        word |= (long)(17 & 0x1F); // code 17
                        word |= ((long)dest & 0xF) << 5; // bits 5..8
                        word |= ((long)src & 0xF) << 9; // bits 9..12
                        long offField = toUnsigned(offset, 16);
                        word |= (offField & 0xFFFFL) << 13; // bits 13..28
                        encoded.add(to7BytesLE(word));
                        break;
                    }
                    case "write": {
                        if (tok.length != 4) throw new IllegalArgumentException("write expects 3 args: write dest src offset");
                        int dest = Integer.parseInt(tok[1]);
                        int src = Integer.parseInt(tok[2]);
                        int offset = Integer.parseInt(tok[3]);
                        word |= (long)(9 & 0x1F); // code 9
                        word |= ((long)dest & 0xF) << 5;
                        word |= ((long)src & 0xF) << 9;
                        long offField = toUnsigned(offset, 16);
                        word |= (offField & 0xFFFFL) << 13;
                        encoded.add(to7BytesLE(word));
                        break;
                    }
                    case "pow": {
                        if (tok.length != 5) throw new IllegalArgumentException("pow expects 4 args: pow dest a b offset");
                        int dest = Integer.parseInt(tok[1]);
                        int a = Integer.parseInt(tok[2]);
                        int b = Integer.parseInt(tok[3]);
                        int offset = Integer.parseInt(tok[4]);
                        word |= (long)(0 & 0x1F); // code 0
                        word |= ((long)dest & 0xF) << 5;  // bits 5..8
                        word |= ((long)a & 0xF) << 9;     // bits 9..12
                        word |= ((long)b & 0xF) << 13;    // bits 13..16
                        long offField = toUnsigned(offset, 16);
                        word |= (offField & 0xFFFFL) << 17; // bits 17..32
                        encoded.add(to7BytesLE(word));
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unknown opcode: " + op);
                }
            } catch (Exception ex) {
                throw new IOException("Error on line " + lineNo + ": " + ex.getMessage(), ex);
            }
        }

        // write file
        try (OutputStream os = Files.newOutputStream(out)) {
            for (byte[] b : encoded) os.write(b);
        }
        System.out.println("Assembled " + encoded.size() + " instructions to " + out);
        System.out.println("Размер двоичного файла: " + encoded.size() * 7 + " байт");
    }

    // helper to produce 7 bytes little-endian from a 56-bit word
    static byte[] to7BytesLE(long word) {
        byte[] b = new byte[7];
        for (int i = 0; i < 7; i++) {
            b[i] = (byte)((word >> (8*i)) & 0xFFL);
        }
        return b;
    }

    // convert signed int value into unsigned field of width bits, checking range
    static long toUnsigned(int value, int bits) {
        int min = -(1 << (bits-1));
        int max = (1 << (bits-1)) - 1;
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value " + value + " out of range for " + bits + "-bit signed field (allowed " + min + ".." + max + ")");
        }
        long mask = (1L<<bits) - 1;
        return (long)value & mask;
    }

    static void putBitsCheck(long _dummy) { /* placeholder */ }

    // ---------- Interpreter ----------
    static void runProgram(Path bin, Path dumpPath) throws IOException {
        byte[] all = Files.readAllBytes(bin);
        if (all.length % 7 != 0) {
            throw new IOException("Binary program size must be multiple of 7 bytes (one instruction). Found: " + all.length);
        }
        int instrCount = all.length / 7;
        int[] memory = new int[MEMORY_SIZE];
        // simple execution: sequential, no jumps (specification didn't include)
        for (int i = 0; i < instrCount; i++) {
            long word = 0;
            for (int b = 0; b < 7; b++) {
                word |= ( (long) (all[i*7 + b] & 0xFF) ) << (8*b); // little-endian
            }
            int opcode = (int)(word & 0x1F); // bits 0..4
            switch (opcode) {
                case 16: { // load
                    int dest = (int)((word >> 5) & 0xF);
                    int constField = (int)((word >> 9) & 0x3FF); // 10 bits
                    int constant = signExtend(constField, 10);
                    checkAddr(dest);
                    memory[dest] = constant;
                    System.out.printf("instr %d: load %d %d -> memory[%d]=%d%n", i, dest, constant, dest, memory[dest]);
                    break;
                }
                case 17: { // read
                    int dest = (int)((word >> 5) & 0xF);
                    int src = (int)((word >> 9) & 0xF);
                    int offField = (int)((word >> 13) & 0xFFFF);
                    int offset = signExtend(offField, 16);
                    int addr = memory[src] + offset;
                    checkAddr(dest);
                    checkAddr(addr);
                    memory[dest] = memory[addr];
                    System.out.printf("instr %d: read %d %d %d -> memory[%d]=memory[%d]=%d%n", i, dest, src, offset, dest, addr, memory[dest]);
                    break;
                }
                case 9: { // write (indirect addressing)
                    int regDest = (int)((word >> 5) & 0xF);   // register containing base address
                    int regSrc  = (int)((word >> 9) & 0xF);   // register containing source address
                    int offField = (int)((word >> 13) & 0xFFFF);
                    int offset = signExtend(offField, 16);

                    // Registers hold addresses
                    checkAddr(regDest);
                    checkAddr(regSrc);

                    int baseAddr = memory[regDest];
                    int srcAddr  = regSrc;

                    checkAddr(baseAddr);
                    checkAddr(srcAddr);

                    int destAddr = baseAddr + offset;
                    checkAddr(destAddr);

                    memory[destAddr] = memory[srcAddr];

                    System.out.printf(
                            "instr %d: write %d %d %d -> memory[%d]=memory[%d]=%d%n",
                            i, regDest, regSrc, offset, destAddr, srcAddr, memory[destAddr]
                    );
                    break;
                }
                case 0: { // pow
                    int dest = (int)((word >> 5) & 0xF);
                    int a = (int)((word >> 9) & 0xF);
                    int b = (int)((word >> 13) & 0xF);
                    int offField = (int)((word >> 17) & 0xFFFF);
                    int offset = signExtend(offField, 16);
                    int aidx = memory[a] + offset;
                    checkAddr(dest);
                    checkAddr(aidx);
                    double res = Math.pow((double)memory[aidx], (double)b);
                    long rval = (long) res;
                    memory[dest] = (int) rval;
                    System.out.printf("instr %d: pow %d %d %d %d -> memory[%d] = %d^%d = %d%n",
                            i, dest, a, b, offset, dest, memory[aidx], memory[b], memory[dest]);
                    break;
                }
                default:
                    throw new IOException("Unknown opcode " + opcode + " at instruction " + i);
            }
        }

        System.out.println("Execution finished. Non-zero memory cells:");
        for (int i = 0; i < memory.length; i++) {
            if (memory[i] != 0) System.out.printf("  [%d] = %d%n", i, memory[i]);
        }
        if (dumpPath != null) {
            dumpMemoryXML(memory, dumpPath);
            System.out.println("Memory dumped to XML file: " + dumpPath);
        }
    }

    static void checkAddr(int a) {
        if (a < 0 || a >= MEMORY_SIZE) throw new RuntimeException("Memory address out of range: " + a);
    }

    // sign-extend value of width bits (bits<=32)
    static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return (value << shift) >> shift;
    }


    static void dumpMemoryXML(int[] memory, Path dumpFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(dumpFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<memory>\n");
            for (int i = 0; i < memory.length; i++) {
                if (memory[i] != 0) {
                    writer.write(String.format("  <cell address=\"%d\">%d</cell>\n", i, memory[i]));
                }
            }
            writer.write("</memory>\n");
        }
    }
}