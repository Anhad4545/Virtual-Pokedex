package org.example;
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;
public class Main {
    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(PokedexUI::new);
    }
}