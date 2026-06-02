package com.shadowwwbyte.smartexpense;

import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DashboardFragment extends Fragment {
    private ExpenseManager manager;
    private LinearLayout dashContent;

    // Gruvbox palette
    static final int BG       = 0xFF282828;
    static final int BG1      = 0xFF3C3836;
    static final int BG2      = 0xFF504945;
    static final int FG       = 0xFFEBDBB2;
    static final int FG2      = 0xFFD5C4A1;
    static final int YELLOW   = 0xFFD79921;
    static final int GREEN    = 0xFF98971A;
    static final int RED      = 0xFFCC241D;
    static final int BLUE     = 0xFF458588;
    static final int ORANGE   = 0xFFD65D0E;
    static final int AQUA     = 0xFF689D6A;
    static final int BRIGHT_G = 0xFFB8BB26;
    static final int BRIGHT_Y = 0xFFFABD2F;
    static final int BRIGHT_R = 0xFFFB4934;

    public DashboardFragment(ExpenseManager manager) { this.manager = manager; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        dashContent = root.findViewById(R.id.dash_content);
        root.findViewById(R.id.btn_export_pdf).setOnClickListener(v -> exportDetailedPdf());
        root.findViewById(R.id.btn_export_png).setOnClickListener(v -> exportPng());
        refresh();
        return root;
    }

    public void refresh() {
        if (dashContent == null || getContext() == null) return;
        dashContent.removeAllViews();

        addSection("People (" + manager.getPeople().size() + ")", YELLOW);
        for (Person p : manager.getPeople()) addRow("\u2022 " + p.getName(), FG);

        double totalExp = manager.getTotalExpenses();
        addSection("Bills (" + manager.getBills().size() + ")  Total: " + String.format("%.2f", totalExp), BLUE);
        for (Bill b : manager.getBills()) {
            Person payer = manager.getPersonById(b.getPayerId());
            addRow("\u2022 " + b.getTitle() + "  " + String.format("%.2f", b.getTotal())
                + (payer != null ? "  [paid by " + payer.getName() + "]" : ""), FG);
            if (!b.getDescription().isEmpty()) addRow("    " + b.getDescription(), FG2);
            if (b.hasIndividualAmounts()) {
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet()) {
                    Person p = manager.getPersonById(e.getKey());
                    addRow("    \u2514 " + (p != null ? p.getName() : "?") + ": " + String.format("%.2f", e.getValue()), FG2);
                }
            }
        }

        addSection("Balances", ORANGE);
        Map<Integer, Double> bal = manager.computeBalances();
        for (Person p : manager.getPeople()) {
            double v = bal.getOrDefault(p.getId(), 0.0);
            int color; String label;
            if (v > 0.01)       { label = "\u25CF " + p.getName() + ": +" + String.format("%.2f", v) + "  (is owed)"; color = BRIGHT_G; }
            else if (v < -0.01) { label = "\u25CF " + p.getName() + ": "  + String.format("%.2f", v) + "  (owes)";    color = BRIGHT_R; }
            else                { label = "\u25CF " + p.getName() + ": settled";                                        color = FG2; }
            addRow(label, color);
        }

        addSection("Settlements", AQUA);
        List<String> tx = manager.settleMinimal();
        if (tx.isEmpty()) addRow("Nothing to settle  \u2714", BRIGHT_G);
        else for (String t : tx) addRow("\u21D2 " + t, BRIGHT_Y);
    }

    private void addSection(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text); tv.setTextColor(color); tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD); tv.setPadding(0, 24, 0, 6);
        dashContent.addView(tv);
        View div = new View(getContext());
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        div.setBackgroundColor(color); dashContent.addView(div);
    }

    private void addRow(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text); tv.setTextColor(color); tv.setTextSize(13.5f); tv.setPadding(8, 4, 0, 4);
        dashContent.addView(tv);
    }

    // ========================= DETAILED PDF =========================

    // PDF page is A4: 595 x 842 pt
    static final int PW = 595, PH = 842;
    static final int ML = 40, MR = 40, MT = 50, MB = 50; // margins
    static final int CW = PW - ML - MR; // content width

    // PDF Paint helpers
    private Paint pdfPaint(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }
    private Paint fillPaint(int color) {
        Paint p = new Paint(); p.setColor(color); p.setStyle(Paint.Style.FILL); return p;
    }
    private Paint strokePaint(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); return p;
    }

    // State for multi-page drawing
    private PdfDocument pdfDoc;
    private PdfDocument.Page currentPage;
    private Canvas pdfCanvas;
    private int pageNum;
    private float curY;
    private String pdfGroupName;

    private void drawPageFooter() {
        Paint fp = pdfPaint(FG2, 9f, false);
        String txt = "Page " + pageNum;
        pdfCanvas.drawText(txt, PW / 2f - fp.measureText(txt) / 2f, PH - 18, fp);
        pdfCanvas.drawRect(ML, PH - MB + 10, PW - MR, PH - MB + 11, fillPaint(BG2));
    }

    // Ensure there's at least `need` pts left on page; if not, finish and start new page
    private void ensureSpace(float need) {
        if (curY + need > PH - MB) {
            drawPageFooter();
            pdfDoc.finishPage(currentPage);
            pageNum++;
            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(PW, PH, pageNum).create();
            currentPage = pdfDoc.startPage(pi);
            pdfCanvas = currentPage.getCanvas();
            pdfCanvas.drawRect(0, 0, PW, PH, fillPaint(BG));
            pdfCanvas.drawRect(0, 0, PW, 36, fillPaint(BG1));
            Paint hp = pdfPaint(BRIGHT_Y, 13f, true);
            pdfCanvas.drawText("Expense Splitter" + (pdfGroupName.isEmpty() ? "" : "  \u2014  " + pdfGroupName), ML, 24, hp);
            Paint dp2 = pdfPaint(FG2, 10f, false);
            String dateStr = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
            pdfCanvas.drawText(dateStr, PW - MR - dp2.measureText(dateStr), 24, dp2);
            curY = MT + 36;
        }
    }

    private void drawSectionHeader(String text, int color) {
        ensureSpace(36);
        curY += 10;
        pdfCanvas.drawRect(ML, curY, PW - MR, curY + 26, fillPaint(color));
        Paint tp = pdfPaint(BG, 12f, true);
        pdfCanvas.drawText(text, ML + 8, curY + 18, tp);
        curY += 30;
    }

    private void drawSubHeader(String text, int color) {
        ensureSpace(24);
        Paint tp = pdfPaint(color, 11f, true);
        pdfCanvas.drawText(text, ML + 4, curY + 14, tp);
        curY += 18;
        pdfCanvas.drawRect(ML + 4, curY, PW - MR, curY + 1, fillPaint(color));
        curY += 5;
    }

    private void drawRow(String left, String right, int color, float indent) {
        ensureSpace(18);
        Paint lp = pdfPaint(color, 10f, false);
        Paint rp = pdfPaint(color, 10f, false);
        pdfCanvas.drawText(left, ML + indent, curY + 12, lp);
        if (right != null && !right.isEmpty()) {
            pdfCanvas.drawText(right, PW - MR - rp.measureText(right), curY + 12, rp);
        }
        curY += 16;
    }

    private void drawRowBold(String left, String right, int color, float indent) {
        ensureSpace(20);
        Paint lp = pdfPaint(color, 10f, true);
        Paint rp = pdfPaint(color, 10f, true);
        pdfCanvas.drawText(left, ML + indent, curY + 13, lp);
        if (right != null && !right.isEmpty()) {
            pdfCanvas.drawText(right, PW - MR - rp.measureText(right), curY + 13, rp);
        }
        curY += 18;
    }

    private void drawDivider(int color) {
        ensureSpace(6);
        pdfCanvas.drawRect(ML, curY + 2, PW - MR, curY + 3, fillPaint(color));
        curY += 7;
    }

    private void drawSpacing(float h) { curY += h; }

    private void exportDetailedPdf() {
        try {
            pdfDoc = new PdfDocument();
            pageNum = 0;
            pdfCanvas = null;

            pdfGroupName = "";
            if (getActivity() != null) {
                TextView tv = getActivity().findViewById(R.id.toolbar_title);
                if (tv != null) pdfGroupName = tv.getText().toString();
            }

            // Start first page manually
            pageNum = 1;
            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(PW, PH, pageNum).create();
            currentPage = pdfDoc.startPage(pi);
            pdfCanvas = currentPage.getCanvas();
            pdfCanvas.drawRect(0, 0, PW, PH, fillPaint(BG));
            pdfCanvas.drawRect(0, 0, PW, 36, fillPaint(BG1));
            Paint hp = pdfPaint(BRIGHT_Y, 13f, true);
            pdfCanvas.drawText("Expense Splitter" + (pdfGroupName.isEmpty() ? "" : "  \u2014  " + pdfGroupName), ML, 24, hp);
            Paint dp = pdfPaint(FG2, 10f, false);
            String dateStr = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
            pdfCanvas.drawText(dateStr, PW - MR - dp.measureText(dateStr), 24, dp);
            curY = MT + 36;

            // ---- TITLE BLOCK ----
            Paint titleP = pdfPaint(BRIGHT_Y, 22f, true);
            pdfCanvas.drawText("Expense Report", ML, curY + 26, titleP);
            curY += 32;
            if (!pdfGroupName.isEmpty()) {
                Paint subP = pdfPaint(FG2, 13f, false);
                pdfCanvas.drawText(pdfGroupName, ML, curY + 16, subP);
                curY += 22;
            }
            pdfCanvas.drawRect(ML, curY, PW - MR, curY + 2, fillPaint(YELLOW));
            curY += 12;

            // ---- SUMMARY BOX ----
            int numPeople = manager.getPeople().size();
            int numBills  = manager.getBills().size();
            double totalAmt = manager.getTotalExpenses();
            double perPerson = numPeople > 0 ? totalAmt / numPeople : 0;

            pdfCanvas.drawRoundRect(new RectF(ML, curY, PW - MR, curY + 70), 6, 6, fillPaint(BG1));
            pdfCanvas.drawRoundRect(new RectF(ML, curY, PW - MR, curY + 70), 6, 6, strokePaint(BG2, 1));
            float bx = ML + 14; float by = curY + 20;
            drawSummaryCell(bx,        by, "People",        "" + numPeople,                  YELLOW);
            drawSummaryCell(bx + 120,  by, "Bills",         "" + numBills,                   BLUE);
            drawSummaryCell(bx + 240,  by, "Total Spent",   String.format("%.2f", totalAmt), BRIGHT_Y);
            drawSummaryCell(bx + 380,  by, "Per Person",    String.format("%.2f", perPerson),AQUA);
            curY += 82;

            // ========== SECTION 1: PEOPLE ==========
            drawSectionHeader("1.  People  (" + numPeople + ")", YELLOW);
            for (int i = 0; i < manager.getPeople().size(); i++) {
                Person p = manager.getPeople().get(i);
                Map<Integer, Double> bal = manager.computeBalances();
                double v = bal.getOrDefault(p.getId(), 0.0);
                String balStr = v > 0.01 ? "+" + String.format("%.2f", v) + " (owed)"
                              : v < -0.01 ? String.format("%.2f", v) + " (owes)"
                              : "settled";
                int balColor = v > 0.01 ? BRIGHT_G : v < -0.01 ? BRIGHT_R : FG2;
                // alternating row bg
                if (i % 2 == 0) {
                    ensureSpace(18);
                    pdfCanvas.drawRect(ML, curY, PW - MR, curY + 16, fillPaint(BG1));
                }
                drawRow((i + 1) + ".  " + p.getName(), balStr, i % 2 == 0 ? FG : FG2, 4);
            }
            drawSpacing(6);

            // ========== SECTION 2: BILLS DETAIL ==========
            drawSectionHeader("2.  Bills Detail", BLUE);

            List<Bill> bills = manager.getBills();
            for (int bi = 0; bi < bills.size(); bi++) {
                Bill b = bills.get(bi);
                Person payer = manager.getPersonById(b.getPayerId());

                ensureSpace(90);
                // Bill card header
                pdfCanvas.drawRoundRect(new RectF(ML, curY, PW - MR, curY + 28), 4, 4, fillPaint(BG1));
                Paint btp = pdfPaint(BRIGHT_Y, 11f, true);
                pdfCanvas.drawText((bi + 1) + ".  " + b.getTitle(), ML + 8, curY + 19, btp);
                Paint bap = pdfPaint(BRIGHT_Y, 11f, true);
                String totalStr = String.format("%.2f", b.getTotal());
                pdfCanvas.drawText(totalStr, PW - MR - bap.measureText(totalStr) - 8, curY + 19, bap);
                curY += 32;

                // Bill meta
                if (!b.getDescription().isEmpty()) {
                    drawRow("Description:  " + b.getDescription(), "", FG2, 8);
                }
                drawRow("Paid by:  " + (payer != null ? payer.getName() : "Unknown"), "", FG, 8);
                drawRow("Split mode:  " + (b.hasIndividualAmounts() ? "Individual amounts" : "Split evenly among " + numPeople), "", FG2, 8);

                // Individual breakdown table
                if (b.hasIndividualAmounts()) {
                    drawSpacing(4);
                    drawSubHeader("Individual Breakdown", BLUE);
                    double checksum = 0;
                    for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet()) {
                        Person ip = manager.getPersonById(e.getKey());
                        checksum += e.getValue();
                        drawRow((ip != null ? ip.getName() : "?"), String.format("%.2f", e.getValue()), FG, 16);
                    }
                    drawDivider(BG2);
                    drawRowBold("Total", String.format("%.2f", checksum), BRIGHT_Y, 16);
                } else {
                    // Evenly split — show each person's share
                    drawSpacing(4);
                    drawSubHeader("Even Split Breakdown", BLUE);
                    double share = numPeople > 0 ? b.getTotal() / numPeople : 0;
                    for (Person p : manager.getPeople()) {
                        String extra = p.getId() == b.getPayerId() ? "  (payer)" : "";
                        drawRow(p.getName() + extra, String.format("%.2f", share), FG, 16);
                    }
                    drawDivider(BG2);
                    drawRowBold("Total", String.format("%.2f", b.getTotal()), BRIGHT_Y, 16);
                }
                drawSpacing(10);
            }

            // ========== SECTION 3: PER-PERSON EXPENSE SUMMARY ==========
            drawSectionHeader("3.  Per-Person Expense Summary", ORANGE);
            drawRow("Person", "Paid    Owes    Balance", FG2, 4);
            drawDivider(ORANGE);

            Map<Integer, Double> bal = manager.computeBalances();
            // compute how much each person actually paid and owes
            for (Person p : manager.getPeople()) {
                double paid = 0, owes = 0;
                for (Bill b : bills) {
                    if (b.getPayerId() == p.getId()) paid += b.getTotal();
                    if (b.hasIndividualAmounts()) {
                        Double amt = b.getIndividualAmounts().get(p.getId());
                        if (amt != null) owes += amt;
                    } else {
                        if (!bills.isEmpty()) owes += b.getTotal() / numPeople;
                    }
                }
                double balance = bal.getOrDefault(p.getId(), 0.0);
                int bc = balance > 0.01 ? BRIGHT_G : balance < -0.01 ? BRIGHT_R : FG2;
                ensureSpace(18);
                String right = String.format("%.2f    %.2f    %s%.2f",
                    paid, owes, balance >= 0 ? "+" : "", balance);
                drawRow(p.getName(), right, bc, 4);
            }
            drawSpacing(6);

            // ========== SECTION 4: BALANCES ==========
            drawSectionHeader("4.  Balances", ORANGE);
            for (Person p : manager.getPeople()) {
                double v = bal.getOrDefault(p.getId(), 0.0);
                String label, right; int color;
                if (v > 0.01)       { label = p.getName(); right = "+" + String.format("%.2f", v) + "  (is owed by others)"; color = BRIGHT_G; }
                else if (v < -0.01) { label = p.getName(); right = String.format("%.2f", v) + "  (owes others)";             color = BRIGHT_R; }
                else                { label = p.getName(); right = "settled";                                                  color = FG2; }
                drawRow(label, right, color, 4);
            }
            drawSpacing(6);

            // ========== SECTION 5: SETTLEMENT PLAN ==========
            drawSectionHeader("5.  Settlement Plan", AQUA);
            List<String> tx = manager.settleMinimal();
            if (tx.isEmpty()) {
                drawRow("Nothing to settle — all balances are even.", "", BRIGHT_G, 4);
            } else {
                for (int i = 0; i < tx.size(); i++) {
                    ensureSpace(18);
                    if (i % 2 == 0) pdfCanvas.drawRect(ML, curY, PW - MR, curY + 16, fillPaint(BG1));
                    drawRow((i + 1) + ".  " + tx.get(i), "", BRIGHT_Y, 4);
                }
            }
            drawSpacing(16);

            // ---- FOOTER on last page ----
            drawPageFooter();
            pdfDoc.finishPage(currentPage);

            // Write and share
            File cacheDir = new File(requireContext().getCacheDir(), "exports");
            cacheDir.mkdirs();
            File outFile = new File(cacheDir, "expense_report.pdf");
            try (FileOutputStream fos = new FileOutputStream(outFile)) { pdfDoc.writeTo(fos); }
            pdfDoc.close();

            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Expense Report"));

        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void drawSummaryCell(float x, float y, String label, String value, int color) {
        Paint lp = pdfPaint(FG2, 8f, false);
        Paint vp = pdfPaint(color, 14f, true);
        pdfCanvas.drawText(label, x, y, lp);
        pdfCanvas.drawText(value, x, y + 18, vp);
    }

    // ========================= PNG export (unchanged) =========================
    private Bitmap renderDashboardBitmap() {
        int width = 900;
        Paint bgPaint = new Paint(); bgPaint.setColor(BG); bgPaint.setStyle(Paint.Style.FILL);
        Paint sectionPaint = new Paint(); sectionPaint.setTextSize(36f); sectionPaint.setTypeface(Typeface.DEFAULT_BOLD); sectionPaint.setAntiAlias(true);
        Paint rowPaint = new Paint(); rowPaint.setTextSize(28f); rowPaint.setAntiAlias(true);
        Paint divPaint = new Paint(); divPaint.setStyle(Paint.Style.FILL);
        Paint titlePaint = new Paint(); titlePaint.setTextSize(44f); titlePaint.setTypeface(Typeface.DEFAULT_BOLD); titlePaint.setColor(BRIGHT_Y); titlePaint.setAntiAlias(true);

        List<Object[]> items = buildRenderItems();
        int totalH = 60;
        for (Object[] item : items) {
            String type = (String) item[0];
            if ("title".equals(type)) totalH += 70;
            else if ("section".equals(type)) totalH += 60;
            else if ("div".equals(type)) totalH += 8;
            else totalH += 34;
        }
        totalH += 40;

        Bitmap bmp = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawRect(0, 0, width, totalH, bgPaint);

        int y = 40;
        for (Object[] item : items) {
            String type = (String) item[0];
            if ("title".equals(type)) {
                titlePaint.setColor((Integer) item[2]);
                canvas.drawText((String) item[1], 32, y + 44, titlePaint);
                y += 70;
            } else if ("section".equals(type)) {
                sectionPaint.setColor((Integer) item[2]);
                canvas.drawText((String) item[1], 32, y + 36, sectionPaint);
                y += 50;
            } else if ("div".equals(type)) {
                divPaint.setColor((Integer) item[1]);
                canvas.drawRect(32, y, width - 32, y + 3, divPaint);
                y += 10;
            } else {
                rowPaint.setColor((Integer) item[2]);
                canvas.drawText((String) item[1], 44, y + 26, rowPaint);
                y += 34;
            }
        }
        return bmp;
    }

    private List<Object[]> buildRenderItems() {
        List<Object[]> items = new ArrayList<>();
        String groupName = "";
        if (getActivity() instanceof MainActivity) {
            TextView tv = getActivity().findViewById(R.id.toolbar_title);
            if (tv != null) groupName = tv.getText().toString();
        }
        items.add(new Object[]{"title", "Expense Splitter" + (groupName.isEmpty() ? "" : " - " + groupName), BRIGHT_Y});
        items.add(new Object[]{"title", "Dashboard", FG2});

        items.add(new Object[]{"section", "People (" + manager.getPeople().size() + ")", YELLOW});
        items.add(new Object[]{"div", YELLOW});
        for (Person p : manager.getPeople()) items.add(new Object[]{"row", "\u2022 " + p.getName(), FG});

        double total = manager.getTotalExpenses();
        items.add(new Object[]{"section", "Bills (" + manager.getBills().size() + ")  Total: " + String.format("%.2f", total), BLUE});
        items.add(new Object[]{"div", BLUE});
        for (Bill b : manager.getBills()) {
            Person payer = manager.getPersonById(b.getPayerId());
            items.add(new Object[]{"row", "\u2022 " + b.getTitle() + "  " + String.format("%.2f", b.getTotal()) + (payer != null ? "  [" + payer.getName() + "]" : ""), FG});
            if (!b.getDescription().isEmpty()) items.add(new Object[]{"row", "    " + b.getDescription(), FG2});
            if (b.hasIndividualAmounts()) {
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet()) {
                    Person p = manager.getPersonById(e.getKey());
                    items.add(new Object[]{"row", "    \u2514 " + (p != null ? p.getName() : "?") + ": " + String.format("%.2f", e.getValue()), FG2});
                }
            }
        }

        items.add(new Object[]{"section", "Balances", ORANGE});
        items.add(new Object[]{"div", ORANGE});
        Map<Integer, Double> bal = manager.computeBalances();
        for (Person p : manager.getPeople()) {
            double v = bal.getOrDefault(p.getId(), 0.0);
            String label; int color;
            if (v > 0.01)       { label = "\u25CF " + p.getName() + ": +" + String.format("%.2f", v) + " (owed)";  color = BRIGHT_G; }
            else if (v < -0.01) { label = "\u25CF " + p.getName() + ": "  + String.format("%.2f", v) + " (owes)"; color = BRIGHT_R; }
            else                { label = "\u25CF " + p.getName() + ": settled";                                     color = FG2; }
            items.add(new Object[]{"row", label, color});
        }

        items.add(new Object[]{"section", "Settlements", AQUA});
        items.add(new Object[]{"div", AQUA});
        List<String> tx = manager.settleMinimal();
        if (tx.isEmpty()) items.add(new Object[]{"row", "Nothing to settle  \u2714", BRIGHT_G});
        else for (String t : tx) items.add(new Object[]{"row", "\u21D2 " + t, BRIGHT_Y});

        return items;
    }

    private void exportPng() {
        try {
            Bitmap bmp = renderDashboardBitmap();
            File cacheDir = new File(requireContext().getCacheDir(), "exports");
            cacheDir.mkdirs();
            File outFile = new File(cacheDir, "dashboard.png");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Dashboard PNG"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
