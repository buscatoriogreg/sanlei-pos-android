package com.sanlei.pos.printer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ESC/POS command builder for 58mm thermal receipt printers (384 dots, 32 chars/line).
 * Compatible with RPP02N and similar printers.
 */
public class EscPosCommands {
    private static final byte[] INIT = {0x1B, 0x40};                    // Initialize
    private static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};
    private static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};
    private static final byte[] DOUBLE_HEIGHT = {0x1B, 0x21, 0x10};
    private static final byte[] NORMAL_SIZE = {0x1B, 0x21, 0x00};
    private static final byte[] CUT = {0x1D, 0x56, 0x00};              // Full cut
    private static final byte[] FEED_3 = {0x1B, 0x64, 0x03};           // Feed 3 lines

    private static final int LINE_WIDTH = 32;
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    public static class ReceiptItem {
        public String name;
        public int quantity;
        public double unitPrice;
        public double total;
        public double discount;
    }

    public static class ReceiptData {
        public String branchName;
        public String branchAddress;
        public String branchPhone;
        public String invoiceNumber;
        public String cashierName;
        public String dateTime;
        public List<ReceiptItem> items;
        public double subtotal;
        public double discountAmount;
        public String discountType;
        public String discountIdNumber;
        public double taxAmount;
        public double totalAmount;
        public double amountPaid;
        public double change;
        public String paymentMethod;
    }

    public static byte[] buildReceipt(ReceiptData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(INIT);

            // Header
            out.write(ALIGN_CENTER);
            out.write(BOLD_ON);
            out.write(DOUBLE_HEIGHT);
            out.write(("SANLEI PHARMACY\n").getBytes());
            out.write(NORMAL_SIZE);
            out.write(BOLD_OFF);
            if (data.branchName != null) out.write((data.branchName + "\n").getBytes());
            if (data.branchAddress != null) out.write((data.branchAddress + "\n").getBytes());
            if (data.branchPhone != null) out.write(("Tel: " + data.branchPhone + "\n").getBytes());
            out.write(dashes());
            out.write(ALIGN_LEFT);

            // Invoice info
            out.write(leftRight("Invoice:", data.invoiceNumber != null ? data.invoiceNumber : "OFFLINE"));
            String dt = data.dateTime != null ? data.dateTime :
                    new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).format(new Date());
            out.write(leftRight("Date:", dt));
            out.write(leftRight("Cashier:", data.cashierName != null ? data.cashierName : ""));
            out.write(dashes());

            // Items header
            out.write(BOLD_ON);
            out.write(String.format("%-12s%4s %7s %7s\n", "Item", "Qty", "Price", "Total").getBytes());
            out.write(BOLD_OFF);
            out.write(dashes());

            // Items
            for (ReceiptItem item : data.items) {
                String name = item.name;
                if (name.length() > LINE_WIDTH) {
                    name = name.substring(0, LINE_WIDTH);
                }
                out.write((name + "\n").getBytes());
                String line = String.format("  %3d x %-8s %8s\n",
                        item.quantity,
                        "P" + formatP(item.unitPrice),
                        "P" + formatP(item.total));
                if (line.length() > LINE_WIDTH + 1) {
                    line = line.substring(0, LINE_WIDTH) + "\n";
                }
                out.write(line.getBytes());
            }

            out.write(dashes());

            // Totals
            out.write(leftRight("Subtotal:", "P" + DF.format(data.subtotal)));

            if (data.discountAmount > 0) {
                String discLabel = "Discount" + (data.discountType != null ? " (" + data.discountType + ")" : "") + ":";
                out.write(leftRight(discLabel, "-P" + DF.format(data.discountAmount)));
                if (data.discountIdNumber != null && !data.discountIdNumber.isEmpty()) {
                    out.write(leftRight("ID No.:", data.discountIdNumber));
                }
            }

            String vatLabel = "SC/PWD".equals(data.discountType) ? "VAT (Exempt):" : "VAT (incl.):";
            out.write(leftRight(vatLabel, "P" + DF.format(data.taxAmount)));

            out.write(dashes());
            out.write(BOLD_ON);
            out.write(DOUBLE_HEIGHT);
            out.write(leftRight("TOTAL:", "P" + DF.format(data.totalAmount)));
            out.write(NORMAL_SIZE);
            out.write(BOLD_OFF);

            String method = data.paymentMethod != null ? data.paymentMethod.toUpperCase() : "CASH";
            out.write(leftRight(method + " Paid:", "P" + DF.format(data.amountPaid)));
            if (data.change > 0) {
                out.write(leftRight("Change:", "P" + DF.format(data.change)));
            }

            out.write(dashes());

            // Footer
            out.write(ALIGN_CENTER);
            out.write("\n".getBytes());
            out.write("Thank you for your purchase!\n".getBytes());
            out.write("\n".getBytes());

            out.write(FEED_3);
            out.write(CUT);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static String formatP(double amount) {
        return DF.format(amount);
    }

    private static byte[] dashes() {
        return ("--------------------------------\n").getBytes();
    }

    private static byte[] leftRight(String left, String right) {
        int spaces = LINE_WIDTH - left.length() - right.length();
        if (spaces < 1) spaces = 1;
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < spaces; i++) sb.append(' ');
        sb.append(right);
        sb.append('\n');
        return sb.toString().getBytes();
    }
}
