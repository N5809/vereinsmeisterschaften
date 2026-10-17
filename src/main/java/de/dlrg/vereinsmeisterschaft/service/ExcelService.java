package de.dlrg.vereinsmeisterschaft.service;

import de.dlrg.vereinsmeisterschaft.model.Models.Participant;
import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelService {
    private static final String[] HEADERS={"Teilnehmer-ID","Vorname","Nachname","Jahrgang","Wertung","Trainingsgruppe"};
    public List<Participant> read(InputStream input) throws IOException {
        try(Workbook book=new XSSFWorkbook(input)) {
            if(book.getNumberOfSheets()==0) throw new IllegalArgumentException("Die Arbeitsmappe enthält kein Tabellenblatt.");
            Sheet sheet=book.getSheetAt(0);
            if(sheet.getLastRowNum()>2000) throw new IllegalArgumentException("Maximal 2000 Teilnehmerzeilen erlaubt. Leere formatierte Zeilen am Ende gegebenenfalls löschen.");
            Row header=sheet.getRow(0);
            if(header==null) throw new IllegalArgumentException("Die erste Zeile muss die Spaltenüberschriften enthalten.");
            DataFormatter formatter=new DataFormatter(Locale.GERMANY);
            Map<String,Integer> columns=new HashMap<>();
            for(Cell cell:header) {
                String name=normalize(formatter.formatCellValue(cell));
                if(!name.isBlank() && columns.put(name,cell.getColumnIndex())!=null)
                    throw new IllegalArgumentException("Doppelte Spaltenüberschrift: "+name);
            }
            for(String required:List.of("teilnehmerid","vorname","nachname","jahrgang"))
                if(!columns.containsKey(required)) throw new IllegalArgumentException("Spalte fehlt: "+required+". Bitte die Vorlage verwenden.");
            List<Participant> list=new ArrayList<>();
            for(int n=1;n<=sheet.getLastRowNum();n++) {
                Row row=sheet.getRow(n); if(row==null) continue;
                String[] values=new String[HEADERS.length];
                for(int i=0;i<HEADERS.length;i++) {
                    Integer c=columns.get(normalize(HEADERS[i])); Cell cell=c==null?null:row.getCell(c);
                    if(cell!=null && cell.getCellType()==CellType.FORMULA)
                        throw new IllegalArgumentException("Zeile "+(n+1)+": bitte feste Werte statt Formeln verwenden.");
                    values[i]=cell==null?"":formatter.formatCellValue(cell).trim();
                }
                if(Arrays.stream(values).allMatch(String::isBlank)) continue;
                try {
                    int year=Integer.parseInt(values[3]);
                    list.add(RaceService.validate(new Participant(values[0],values[1],values[2],year,values[4],values[5])));
                } catch(IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Zeile "+(n+1)+": "+(ex instanceof NumberFormatException?"Jahrgang muss eine vierstellige Jahreszahl sein.":ex.getMessage()));
                }
            }
            return list;
        }
    }
    private String normalize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-\\uFEFF]",""); }

    public byte[] template(boolean demo) throws IOException {
        try(Workbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet sheet=book.createSheet("Teilnehmer");
            CellStyle heading=book.createCellStyle(); Font font=book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(font); heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row row=sheet.createRow(0); row.setHeightInPoints(24);
            for(int i=0;i<HEADERS.length;i++) { Cell c=row.createCell(i); c.setCellValue(HEADERS[i]); c.setCellStyle(heading); sheet.setColumnWidth(i,(i==5?24:19)*256); }
            CellStyle text=book.createCellStyle(); text.setDataFormat(book.createDataFormat().getFormat("@")); sheet.setDefaultColumnStyle(0,text);
            if(demo) {
                String[][] examples={{"1001","Mia","Beispiel","2016","weiblich","Hechte"},{"1002","Leon","Muster","2016","männlich","Hechte"},{"1003","Emma","Test","2017","weiblich","Hechte"},
                        {"1004","Finn","Beispiel","2015","männlich","Tümmler"},{"1005","Lina","Muster","2014","weiblich","Tümmler"},{"1006","Noah","Test","2015","männlich","Tümmler"}};
                for(int n=0;n<examples.length;n++) { Row r=sheet.createRow(n+1); for(int c=0;c<6;c++) { Cell cell=r.createCell(c); if(c==3) cell.setCellValue(Integer.parseInt(examples[n][c])); else cell.setCellValue(examples[n][c]); } }
            }
            sheet.createFreezePane(0,1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,demo?6:1,0,5));
            book.write(out); return out.toByteArray();
        }
    }
}
