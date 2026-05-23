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
    static final int PURPLE   = 0xFFB16286;
    static final int AQUA     = 0xFF689D6A;
    static final int BRIGHT_G = 0xFFB8BB26;
    static final int BRIGHT_Y = 0xFFFABD2F;
    static final int BRIGHT_R = 0xFFFB4934;

    public DashboardFragment(ExpenseManager manager) { this.manager = manager; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        dashContent = root.findViewById(R.id.dash_content);

        root.findViewById(R.id.btn_export_pdf).setOnClickListener(v -> exportPdf());
        root.findViewById(R.id.btn_export_png).setOnClickListener(v -> exportPng());

        refresh();
        return root;
    }

    public void refresh() {
        if (dashContent == null) return;
        dashContent.removeAllViews();

        addSection("People (" + manager.getPeople().size() + ")", YELLOW);
        for (Person p : manager.getPeople()) addRow("\u2022 " + p.getName(), FG);

        double totalExp = manager.getTotalExpenses();
        addSection("Bills (" + manager.getBills().size() + ")  Total: " + String.format("%.2f", totalExp), BLUE);
        for (Bill b : manager.getBills()) {
            Person payer = manager.getPersonById(b.getPayerId());
            addRow("\u2022 " + b.getTitle() + "  " + String.format("%.2f", b.getTotal()) + (payer != null ? "  [paid by " + payer.getName() + "]" : ""), FG);
            if (!b.getDescription().isEmpty()) addRow("  " + b.getDescription(), FG2);
        }

        addSection("Balances", ORANGE);
        Map<Integer, Double> bal = manager.computeBalances();
        for (Person p : manager.getPeople()) {
            double v = bal.getOrDefault(p.getId(), 0.0);
            String status; int color;
            if (v > 0.01) { status = "  +"; color = BRIGHT_G; }
            else if (v < -0.01) { status = "  -"; color = BRIGHT_R; }
            else { status = "  \u2714"; color = FG2; }
            String label = "\u25CF " + p.getName() + ": " + String.format("%.2f", v) + status;
            addRow(label, color);
        }

        addSection("Settlements", AQUA);
        List<String> tx = manager.settleMinimal();
        if (tx.isEmpty()) addRow("Nothing to settle  \u2714", BRIGHT_G);
        else for (String t : tx) addRow("\u21D2 " + t, BRIGHT_Y);
    }

    private void addSection(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 24, 0, 6);
        dashContent.addView(tv);
        View div = new View(getContext());
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        div.setBackgroundColor(color);
        dashContent.addView(div);
    }

    private void addRow(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(13.5f);
        tv.setPadding(8, 4, 0, 4);
        dashContent.addView(tv);
    }

    // ---- Export ----
    private Bitmap renderDashboardBitmap() {
        int width = 900;
        Paint bgPaint = new Paint(); bgPaint.setColor(BG); bgPaint.setStyle(Paint.Style.FILL);
        Paint sectionPaint = new Paint(); sectionPaint.setTextSize(36f); sectionPaint.setTypeface(Typeface.DEFAULT_BOLD); sectionPaint.setAntiAlias(true);
        Paint rowPaint = new Paint(); rowPaint.setTextSize(30f); rowPaint.setAntiAlias(true);
        Paint divPaint = new Paint(); divPaint.setStyle(Paint.Style.FILL); divPaint.setStrokeWidth(2f);
        Paint titlePaint = new Paint(); titlePaint.setTextSize(44f); titlePaint.setTypeface(Typeface.DEFAULT_BOLD); titlePaint.setColor(BRIGHT_Y); titlePaint.setAntiAlias(true);

        // First pass: measure height
        List<Object[]> items = buildRenderItems();
        int totalH = 60;
        for (Object[] item : items) {
            String type = (String) item[0];
            if ("title".equals(type)) totalH += 70;
            else if ("section".equals(type)) totalH += 60;
            else if ("div".equals(type)) totalH += 8;
            else totalH += 36;
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
            } else { // row
                rowPaint.setColor((Integer) item[2]);
                canvas.drawText((String) item[1], 44, y + 28, rowPaint);
                y += 36;
            }
        }
        return bmp;
    }

    private List<Object[]> buildRenderItems() {
        List<Object[]> items = new ArrayList<>();
        String groupName = "";
        if (getActivity() instanceof MainActivity) {
            android.widget.TextView tv = getActivity().findViewById(R.id.toolbar_title);
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
            if (!b.getDescription().isEmpty()) items.add(new Object[]{"row", "  " + b.getDescription(), FG2});
        }

        items.add(new Object[]{"section", "Balances", ORANGE});
        items.add(new Object[]{"div", ORANGE});
        Map<Integer, Double> bal = manager.computeBalances();
        for (Person p : manager.getPeople()) {
            double v = bal.getOrDefault(p.getId(), 0.0);
            String label; int color;
            if (v > 0.01) { label = "\u25CF " + p.getName() + ": +" + String.format("%.2f", v) + " (owed)"; color = BRIGHT_G; }
            else if (v < -0.01) { label = "\u25CF " + p.getName() + ": " + String.format("%.2f", v) + " (owes)"; color = BRIGHT_R; }
            else { label = "\u25CF " + p.getName() + ": settled"; color = FG2; }
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

    private void exportPdf() {
        try {
            Bitmap bmp = renderDashboardBitmap();
            PdfDocument doc = new PdfDocument();
            float scale = 595f / bmp.getWidth();
            int pageH = Math.max(1, (int)(bmp.getHeight() * scale));
            PdfDocument.PageInfo pi = new PdfDocument.PageInfo.Builder(595, pageH, 1).create();
            PdfDocument.Page page = doc.startPage(pi);
            Canvas c = page.getCanvas();
            Matrix m = new Matrix(); m.setScale(scale, scale);
            c.drawBitmap(bmp, m, null);
            doc.finishPage(page);

            File cacheDir = new File(requireContext().getCacheDir(), "exports");
            cacheDir.mkdirs();
            File outFile = new File(cacheDir, "dashboard.pdf");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                doc.writeTo(fos);
            }
            doc.close();

            Uri uri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Dashboard PDF"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
