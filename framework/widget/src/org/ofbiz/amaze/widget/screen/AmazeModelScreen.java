package org.ofbiz.amaze.widget.screen;

import org.ofbiz.base.util.UtilXml;
import org.ofbiz.widget.screen.ModelScreen;
import org.w3c.dom.Element;

import java.util.Map;

/**
 * Created by Administrator on 2015/1/14.
 */
public class AmazeModelScreen extends ModelScreen {

    // ===== CONSTRUCTORS =====
    /** Default Constructor */
    protected AmazeModelScreen() {}

    /** XML Constructor */
    public AmazeModelScreen(Element screenElement, Map<String, ModelScreen> modelScreenMap, String sourceLocation) {

        super(screenElement,modelScreenMap,sourceLocation);

        // read in the section, which will read all sub-widgets too
        Element sectionElement = UtilXml.firstChildElement(screenElement, "section");
        if (sectionElement == null) {
            throw new IllegalArgumentException("No section found for the screen definition with name: " + this.name);
        }
        this.section = new AmazeModelScreenWidget.Section(this, sectionElement);
        this.section.isMainSection = true;
    }
}
