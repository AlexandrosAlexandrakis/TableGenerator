﻿package graphtorelation;

import org.xmlmiddleware.schemas.dtds.*;
import org.xmlmiddleware.xmlutils.XMLName;

import java.io.*;
import java.util.*;

import org.xml.sax.*;


public class TablesGenerator {
    //**************************************************************************
   // Variables
   //**************************************************************************

   Writer out;
   String createAllTablesQuery="";
   //**************************************************************************
   // Constants
   //**************************************************************************

   private static final String NEWLINE = System.getProperty("line.separator");
   private static final String INDENT = "   ";

   //**************************************************************************
   // Constructors
   //**************************************************************************

   /** Construct a new TableGenerator. */
   public TablesGenerator()
   {
   }
   
   //**************************************************************************
   // Private methods -- process element type definitions
   //**************************************************************************

   public void generateDTD(InputSource src)
      throws Exception
   {
      DTDParser parser = new DTDParser();
      DTD dtd;

      dtd = parser.parseExternalSubset(src, null);
      processElementTypes(dtd);
      System.out.println(createAllTablesQuery);
      FileWriter catq= new FileWriter("createAllTables.sql");
      catq.write(createAllTablesQuery);
      catq.close();
   }

   private void processElementTypes(DTD dtd)
      throws Exception
   {
      Enumeration e;

      e = dtd.elementTypes.elements();
      while (e.hasMoreElements())
      {
         processElementType((ElementType)e.nextElement());
      }
   }

   private void processElementType(ElementType elementType)
      throws Exception
   {
      // Check if the element is treated as a Table. If not, return and don't
      // process it now. Instead, we will process it when we encounter it in
      // each of its parents.

     // if (!isTable(elementType)) return;

      // Open a new FileWriter for the query.

      out = new FileWriter(elementType.name.getLocalName() + ".sql");

      // Write a 'create table' query for the element type.
     
      out.write(NEWLINE);
      out.write("CREATE TABLE ");
      out.write(elementType.name.getLocalName());
      out.write("(");
      out.write(NEWLINE);
      out.write(INDENT);
      out.write("ID INT IDENTITY(1,1) PRIMARY KEY,");
      out.write(NEWLINE);
      out.write(INDENT);
      out.write("RTN_ID INT FOREIGN KEY REFERENCES RTN(ID),");
      out.write(NEWLINE);
      out.write(INDENT);
      createAllTablesQuery+="CREATE TABLE "+elementType.name.getLocalName()+"("+NEWLINE+INDENT+"ID INT IDENTITY(1,1) PRIMARY KEY,"+NEWLINE+INDENT;
      createAllTablesQuery+="RTN_ID INT FOREIGN KEY REFERENCES RTN(ID),"+NEWLINE+INDENT;
      Hashtable<Enumeration,Collections> hash=  elementType.parents;
      Enumeration en =hash.elements();
      while( en. hasMoreElements() )
      {
          ElementType ee= (ElementType) en.nextElement();
          out.write(ee.name.getLocalName()+"_ID");
          out.write(" INT FOREIGN KEY REFERENCES "+ee.name.getLocalName() +"(ID)");
          out.write(",");
          out.write(NEWLINE);
          out.write(INDENT);
          createAllTablesQuery+=ee.name.getLocalName()+"_ID"+" INT FOREIGN KEY REFERENCES "+ee.name.getLocalName() +"(ID),"+NEWLINE+INDENT;


          
      }
      out.write("DEW_POS VARCHAR(MAX)");
      out.write(",");
      out.write(NEWLINE);
      out.write(INDENT);
      out.write("DOC_ID INT");
      out.write(",");
      out.write(NEWLINE);
      out.write(INDENT);
      createAllTablesQuery+="DEW_POS VARCHAR(MAX),"+NEWLINE+INDENT+"DOC_ID INT,"+NEWLINE+INDENT;
     
      //out.write(NEWLINE);

      // Process the attributes, adding one property for each.

      processAttributes(elementType.attributes);

      // Process the content, adding properties for each child element.

      switch (elementType.contentType)
      {
         case ElementType.CONTENT_ANY:
         case ElementType.CONTENT_MIXED:
            throw new Exception("Can't process element types with mixed or ANY content: " + elementType.name.getUniversalName());

       /* case ElementType.CONTENT_ELEMENT:
           processElementContent(elementType.content, elementType.children);
           break;*/

        case ElementType.CONTENT_PCDATA:
           processPCDATAContent(elementType);
           break;

        case ElementType.CONTENT_EMPTY:
           // No content to process.
           break;
      }

      // Close the Table.
      out.write(NEWLINE);
      out.write(")");
      out.write(NEWLINE);
      out.write(NEWLINE);
      createAllTablesQuery+=NEWLINE+")"+NEWLINE+NEWLINE;
      // Close the file

      out.close();
   }

   private boolean isTable(ElementType elementType)
   {
      // If an element type has any attributes or child elements, it is
      // treated as a Table. Otherwise, it is treated as a property.

      // BUG! This code actually misses a special case. If an element type
      // has no children, no attributes, and no parents, it needs to be
      // treated as a Table. However, the corresponding XML document is:
      //
      //    <?xml version="1.0"?>
      //    <!DOCTYPE [<!ELEMENT foo EMPTY>]>
      //    <foo/>
      //
      // which really isn't worth worrying about...

      return (!elementType.children.isEmpty() ||
              !elementType.attributes.isEmpty());
   }

   private void processAttributes(Hashtable attributes)
      throws Exception
   {
      Enumeration e;
      Attribute   attribute;

      // Add a property for each attribute of the element (if any).

      e = attributes.elements();
      while (e.hasMoreElements())
      {
         attribute = (Attribute)e.nextElement();
         addAttrProperty(attribute);
      }
   }

   private void addAttrProperty(Attribute attribute)
      throws Exception
   {
      boolean multiValued;

      multiValued = ((attribute.type == Attribute.TYPE_IDREFS) ||
                     (attribute.type == Attribute.TYPE_ENTITIES) ||
                     (attribute.type == Attribute.TYPE_NMTOKENS));

      addScalarProperty(attribute.name, multiValued);
   }

   private void addScalarProperty(XMLName name, boolean multiValued)
      throws Exception
   {
      // Add a property of the form:
      //
      // String m_ElementTypeName;
      //
      // or (for multiply-occurring children or multi-valued attributes):
      //
      // String[] m_ElementTypeNames;

      if (!multiValued)
      {
          //out.write(",");
          out.write(NEWLINE);
          out.write(INDENT);
          out.write("m_");
          out.write(name.getLocalName());
          out.write(" VARCHAR(50)");
          createAllTablesQuery+=NEWLINE+INDENT+"m_"+name.getLocalName()+" VARCHAR(50)";

      }else
      {
         // out.write(",");
          /*out.write(NEWLINE);
          out.write(INDENT);
          out.write("m_");
          out.write(name.getLocalName());
          */
      }
   }

   private void processElementContent(Group content, Hashtable children)
      throws Exception
   {
      Enumeration e;
      ElementType child;
      Hashtable   repeatInfo = new Hashtable();
      boolean     repeatable;

      // Determine which element types-as-properties are repeatable. We
      // need this information to decide whether to map them to arrays or
      // single-valued properties.

      setRepeatInfo(repeatInfo, content, content.isRepeatable);

      // Process the children and either add Table or scalar properties for them.

      e = children.elements();
      while (e.hasMoreElements())
      {
         child = (ElementType)e.nextElement();
         repeatable = ((Boolean)repeatInfo.get(child.name)).booleanValue();
         if (isTable(child))
         {
            addTableProperty(child, repeatable);
         }
         else
         {
            addElementTypeProperty(child, repeatable);
         }
      }
   }

   private void setRepeatInfo(Hashtable repeatInfo, Group content, boolean parentRepeatable)
   {
      Particle    particle;
      boolean     repeatable;
      ElementType child;

      for (int i = 0; i < content.members.size(); i++)
      {
         // Get the content particle and determine if it is repeatable.
         // A content particle is repeatable if it is repeatable or its
         // parent is repeatable.

         particle = (Particle)content.members.elementAt(i);
         repeatable = parentRepeatable || particle.isRepeatable;

         // Process the content particle.
         //
         // If the content particle is a reference to an element type,
         // save information about whether the property is repeatable.
         // If the content particle is a group, process it recursively.

         if (particle.type == Particle.TYPE_ELEMENTTYPEREF)
         {
            child = ((Reference)particle).elementType;
            repeatInfo.put(child.name, new Boolean(repeatable));
         }
         else // particle.type == Particle.TYPE_CHOICE || Particle.TYPE_SEQUENCE
         {
            setRepeatInfo(repeatInfo, (Group)particle, repeatable);
         }
      }
   }

    private void addPCDATAProperty(ElementType elementType)
      throws Exception
   {
      XMLName name;

      name = XMLName.create(null, elementType.name.getLocalName() + "PCDATA", null);
      addScalarProperty(name, false);
   }

   private void addElementTypeProperty(ElementType elementType, boolean multiValued)
      throws Exception
   {
      addScalarProperty(elementType.name, multiValued);
   }

   private void addTableProperty(ElementType elementType, boolean multiValued)
      throws Exception
   {
      // Add a property of the form:
      //
      // ElementTypeName m_ElementTypeName;
      //
      // or (for multiply-occurring children):
      //
      // ElementTypeName[] m_ElementTypeNames;
      if (!multiValued)
      {
         // out.write(",");
          out.write(NEWLINE);
          out.write(INDENT);
          out.write("m_");
          out.write(elementType.name.getLocalName());
          out.write(" VARCHAR(50)");
          createAllTablesQuery+=NEWLINE+INDENT+"m_"+elementType.name.getLocalName()+" VARCHAR(50)";

      }else
      {
         // out.write(",");
         /* out.write(NEWLINE);
          out.write(INDENT);
          out.write("m_");
          out.write(elementType.name.getLocalName());
         */
      }
      
   }

   private void processPCDATAContent(ElementType elementType)
      throws Exception
   {
      // This is the special case where the element type has attributes
      // but no child element types. In this case, we create a property
      // in the Table for the PCDATA. (Hence, the argument is false, meaning
      // that the PCDATA is single-valued.)

      addPCDATAProperty(elementType);
   }

   



}
