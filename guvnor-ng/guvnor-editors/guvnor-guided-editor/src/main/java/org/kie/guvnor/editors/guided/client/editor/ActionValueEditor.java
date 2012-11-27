/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.guvnor.editors.guided.client.editor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import org.kie.guvnor.commons.ui.client.widget.PopupDatePicker;
import org.kie.guvnor.datamodel.api.shared.DropDownData;
import org.kie.guvnor.editors.guided.client.editor.events.TemplateVariablesChangedEvent;
import org.kie.guvnor.editors.guided.client.resources.DroolsGuvnorImages;
import org.kie.guvnor.editors.guided.client.resources.GuvnorImages;
import org.kie.guvnor.editors.guided.client.resources.i18n.Constants;
import org.kie.guvnor.editors.guided.client.widget.TextBoxFactory;
import org.kie.guvnor.editors.guided.model.ActionFieldValue;
import org.kie.guvnor.editors.guided.model.ActionInsertFact;
import org.kie.guvnor.editors.guided.model.DataType;
import org.kie.guvnor.editors.guided.model.FactPattern;
import org.kie.guvnor.editors.guided.model.FieldConstraint;
import org.kie.guvnor.editors.guided.model.FieldNatureType;
import org.kie.guvnor.editors.guided.model.RuleModel;
import org.kie.guvnor.editors.guided.model.SingleFieldConstraint;
import org.uberfire.client.common.DirtyableComposite;
import org.uberfire.client.common.DropDownValueChanged;
import org.uberfire.client.common.FormStylePopup;
import org.uberfire.client.common.InfoPopup;
import org.uberfire.client.common.SmallLabel;

/**
 * This provides for editing of fields in the RHS of a rule.
 */
public class ActionValueEditor
        extends DirtyableComposite {

    private ActionFieldValue value;
    private DropDownData     enums;
    private SimplePanel      root;
    private RuleModeller     modeller;
    private RuleModel        model;
    private EventBus         eventBus;
    private String variableType = null;
    private boolean readOnly;
    private Command onChangeCommand;

    public ActionValueEditor( final ActionFieldValue val,
                              final DropDownData enums,
                              RuleModeller modeller,
                              EventBus eventBus,
                              String variableType,
                              boolean readOnly ) {

        this.readOnly = readOnly;

        if ( val.getType().equals( DataType.TYPE_BOOLEAN ) ) {
            this.enums = DropDownData.create( new String[]{ "true", "false" } );
        } else {
            this.enums = enums;
        }
        this.root = new SimplePanel();
        this.value = val;
        this.modeller = modeller;
        this.model = modeller.getModel();
        this.eventBus = eventBus;
        this.variableType = variableType;
        refresh();
        initWidget( root );
    }

    private void refresh() {
        root.clear();

        //If undefined let the user pick
        if ( value.getNature() == FieldNatureType.TYPE_UNDEFINED ) {

            //Automatic decisions regarding FieldNature
            if ( value.getValue() != null && value.getValue().length() > 0 ) {
                if ( value.getValue().charAt( 0 ) == '=' ) {
                    value.setNature( FieldNatureType.TYPE_VARIABLE );
                } else {
                    value.setNature( FieldNatureType.TYPE_LITERAL );
                }
            } else {
                root.add( choice() );
                return;
            }
        }

        //Template TextBoxes are always Strings as they hold the template key for the actual value
        if ( value.getNature() == FieldNatureType.TYPE_TEMPLATE ) {
            Widget box = wrap( templateKeyEditor() );
            root.add( box );
            return;
        }

        //Variable fields (including bound enumeration fields)
        if ( value.getNature() == FieldNatureType.TYPE_VARIABLE ) {
            Widget list = wrap( boundVariable() );
            root.add( list );
            return;
        }

        //Enumerations - since this does not use FieldNature it should follow those that do
        if ( enums != null && ( enums.fixedList != null || enums.queryExpression != null ) ) {
            Widget list = wrap( enumEditor() );
            root.add( list );
            return;
        }

        //Formula require a 
        if ( value.getNature() == FieldNatureType.TYPE_FORMULA ) {
            Widget box = wrap( formulaEditor() );
            root.add( box );
            return;
        }

        //Fall through for all remaining FieldNatures
        Widget box = wrap( literalEditor() );
        root.add( box );

    }

    //Wrap a Constraint Value Editor with an icon to remove the type 
    private Widget wrap( Widget w ) {
        HorizontalPanel wrapper = new HorizontalPanel();
        Image clear = DroolsGuvnorImages.INSTANCE.DeleteItemSmall();
        clear.setAltText( Constants.INSTANCE.RemoveActionValueDefinition() );
        clear.setTitle( Constants.INSTANCE.RemoveActionValueDefinition() );
        clear.addClickHandler( new ClickHandler() {

            public void onClick( ClickEvent event ) {
                //Reset Constraint's value and value type
                if ( Window.confirm( Constants.INSTANCE.RemoveActionValueDefinitionQuestion() ) ) {
                    value.setNature( FieldNatureType.TYPE_UNDEFINED );
                    value.setValue( null );
                    doTypeChosen();
                }
            }

        } );

        wrapper.add( w );
        if ( !this.readOnly ) {
            wrapper.add( clear );
            wrapper.setCellVerticalAlignment( clear,
                                              HasVerticalAlignment.ALIGN_MIDDLE );
        }
        return wrapper;
    }

    private void doTypeChosen() {
        makeDirty();
        executeOnChangeCommand();
        executeOnTemplateVariablesChange();
        refresh();
    }

    private void doTypeChosen( FormStylePopup form ) {
        doTypeChosen();
        form.hide();
    }

    private Widget boundVariable() {
        // If there is a bound variable that is the same type of the current variable type, then display a list
        ListBox listVariable = new ListBox();
        listVariable.addItem( Constants.INSTANCE.Choose() );
        List<String> bindings = getApplicableBindings();
        for ( String v : bindings ) {
            listVariable.addItem( v );
        }

        //Pre-select applicable item
        if ( value.getValue().equals( "=" ) ) {
            listVariable.setSelectedIndex( 0 );
        } else {
            for ( int i = 0; i < listVariable.getItemCount(); i++ ) {
                if ( listVariable.getItemText( i ).equals( value.getValue().substring( 1 ) ) ) {
                    listVariable.setSelectedIndex( i );
                }
            }
        }

        //Add event handler
        if ( listVariable.getItemCount() > 0 ) {
            listVariable.addChangeHandler( new ChangeHandler() {

                public void onChange( ChangeEvent event ) {
                    ListBox w = (ListBox) event.getSource();
                    value.setValue( "=" + w.getValue( w.getSelectedIndex() ) );
                    executeOnChangeCommand();
                    makeDirty();
                    refresh();
                }
            } );
        }

        if ( this.readOnly ) {
            return new SmallLabel( listVariable.getItemText( listVariable.getSelectedIndex() ) );
        }

        return listVariable;
    }

    private String assertValue() {
        if ( value.getValue() == null ) {
            return "";
        }
        return value.getValue();
    }

    private Widget enumEditor() {
        if ( this.readOnly ) {
            return new SmallLabel( assertValue() );
        }

        EnumDropDown enumDropDown = new EnumDropDown( value.getValue(),
                                                      new DropDownValueChanged() {

                                                          public void valueChanged( String newText,
                                                                                    String newValue ) {
                                                              value.setValue( newValue );
                                                              executeOnChangeCommand();
                                                              makeDirty();
                                                          }
                                                      },
                                                      enums );
        return enumDropDown;
    }

    private Widget literalEditor() {
        if ( this.readOnly ) {
            return new SmallLabel( assertValue() );
        }

        //Date picker
        if ( DataType.TYPE_DATE.equals( value.getType() ) ) {
            final PopupDatePicker dp = new PopupDatePicker( false );

            // Wire up update handler
            dp.addValueChangeHandler( new ValueChangeHandler<Date>() {

                public void onValueChange( ValueChangeEvent<Date> event ) {
                    value.setValue( PopupDatePicker.convertToString( event ) );
                }

            } );

            dp.setValue( assertValue() );
            return dp;
        }

        //Default editor for all other literals
        final TextBox box = TextBoxFactory.getTextBox( value.getType() );
        box.setStyleName( "constraint-value-Editor" );
        box.addChangeHandler( new ChangeHandler() {

            public void onChange( ChangeEvent event ) {
                value.setValue( box.getText() );
                executeOnChangeCommand();
                makeDirty();
            }
        } );
        box.setText( assertValue() );
        attachDisplayLengthHandler( box );
        return box;
    }

    /**
     * An editor for Template Keys
     */
    private Widget templateKeyEditor() {
        if ( this.readOnly ) {
            return new SmallLabel( assertValue() );
        }

        TemplateKeyTextBox box = new TemplateKeyTextBox();
        box.addValueChangeHandler( new ValueChangeHandler<String>() {

            @Override
            public void onValueChange( ValueChangeEvent<String> event ) {
                value.setValue( event.getValue() );
                executeOnChangeCommand();
            }

        } );
        //FireEvents as the box could assume a default value
        box.setValue( assertValue(),
                      true );
        attachDisplayLengthHandler( box );
        return box;
    }

    /**
     * An editor for formula
     * @return
     */
    private Widget formulaEditor() {
        if ( this.readOnly ) {
            return new SmallLabel( assertValue() );
        }

        final TextBox box = new TextBox();
        box.addValueChangeHandler( new ValueChangeHandler<String>() {

            @Override
            public void onValueChange( ValueChangeEvent<String> event ) {
                value.setValue( event.getValue() );
                executeOnChangeCommand();
            }

        } );
        //FireEvents as the box could assume a default value
        box.setValue( assertValue(),
                      true );
        attachDisplayLengthHandler( box );
        return box;
    }

    //Only display the number of characters that have been entered
    private void attachDisplayLengthHandler( final TextBox box ) {
        int length = box.getText().length();
        box.setVisibleLength( length > 0 ? length : 1 );
        box.addKeyUpHandler( new KeyUpHandler() {

            public void onKeyUp( KeyUpEvent event ) {
                int length = box.getText().length();
                box.setVisibleLength( length > 0 ? length : 1 );
            }
        } );
    }

    private Widget choice() {
        if ( this.readOnly ) {
            return new HTML();
        } else {
            Image clickme = GuvnorImages.INSTANCE.Edit();
            clickme.addClickHandler( new ClickHandler() {
                public void onClick( ClickEvent event ) {
                    showTypeChoice( (Widget) event.getSource() );
                }
            } );
            return clickme;
        }
    }

    protected void showTypeChoice( Widget w ) {
        final FormStylePopup form = new FormStylePopup( DroolsGuvnorImages.INSTANCE.Wizard(),
                                                        Constants.INSTANCE.FieldValue() );
        Button lit = new Button( Constants.INSTANCE.LiteralValue() );
        lit.addClickHandler( new ClickHandler() {

            public void onClick( ClickEvent event ) {
                value.setNature( FieldNatureType.TYPE_LITERAL );
                value.setValue( "" );
                doTypeChosen( form );
            }
        } );

        form.addAttribute( Constants.INSTANCE.LiteralValue() + ":",
                           widgets( lit,
                                    new InfoPopup( Constants.INSTANCE.Literal(),
                                                   Constants.INSTANCE.ALiteralValueMeansTheValueAsTypedInIeItsNotACalculation() ) ) );

        if ( modeller.isTemplate() ) {
            Button templateButton = new Button( Constants.INSTANCE.TemplateKey() );
            templateButton.addClickHandler( new ClickHandler() {
                public void onClick( ClickEvent event ) {
                    value.setNature( FieldNatureType.TYPE_TEMPLATE );
                    value.setValue( "" );
                    doTypeChosen( form );
                }
            } );
            form.addAttribute( Constants.INSTANCE.TemplateKey() + ":",
                               widgets( templateButton,
                                        new InfoPopup( Constants.INSTANCE.Literal(),
                                                       Constants.INSTANCE.ALiteralValueMeansTheValueAsTypedInIeItsNotACalculation() ) ) );
        }

        form.addRow( new HTML( "<hr/>" ) );
        form.addRow( new SmallLabel( Constants.INSTANCE.AdvancedSection() ) );

        Button formula = new Button( Constants.INSTANCE.Formula() );
        formula.addClickHandler( new ClickHandler() {

            public void onClick( ClickEvent event ) {
                value.setNature( FieldNatureType.TYPE_FORMULA );
                value.setValue( "=" );
                doTypeChosen( form );
            }
        } );

        // If there is a bound Facts or Fields that are of the same type as the current variable type, then show a button
        List<String> bindings = getApplicableBindings();
        if ( bindings.size() > 0 ) {
            Button variable = new Button( Constants.INSTANCE.BoundVariable() );
            form.addAttribute( Constants.INSTANCE.BoundVariable() + ":",
                               variable );
            variable.addClickHandler( new ClickHandler() {

                public void onClick( ClickEvent event ) {
                    value.setNature( FieldNatureType.TYPE_VARIABLE );
                    value.setValue( "=" );
                    doTypeChosen( form );
                }
            } );
        }

        form.addAttribute( Constants.INSTANCE.Formula() + ":",
                           widgets( formula,
                                    new InfoPopup( Constants.INSTANCE.Formula(),
                                                   Constants.INSTANCE.FormulaTip() ) ) );

        form.show();
    }

    private List<String> getApplicableBindings() {
        List<String> bindings = new ArrayList<String>();

        //Examine LHS Fact and Field bindings and RHS (new) Fact bindings
        for ( String v : modeller.getModel().getAllVariables() ) {

            //LHS FactPattern
            FactPattern fp = modeller.getModel().getLHSBoundFact( v );
            if ( fp != null ) {
                if ( isLHSFactTypeEquivalent( v ) ) {
                    bindings.add( v );
                }
            }

            //LHS FieldConstraint
            FieldConstraint fc = modeller.getModel().getLHSBoundField( v );
            if ( fc != null ) {
                if ( isLHSFieldTypeEquivalent( v ) ) {
                    bindings.add( v );
                }
            }

            //RHS ActionInsertFact
            ActionInsertFact aif = modeller.getModel().getRHSBoundFact( v );
            if ( aif != null ) {
                if ( isRHSFieldTypeEquivalent( v ) ) {
                    bindings.add( v );
                }
            }
        }

        return bindings;
    }

    private boolean isLHSFactTypeEquivalent( String boundVariable ) {
        String boundFactType = modeller.getModel().getLHSBoundFact( boundVariable ).getFactType();

        //If the types are SuggestionCompletionEngine.TYPE_COMPARABLE check the enums are equivalent
        if ( boundFactType.equals( DataType.TYPE_COMPARABLE ) ) {
            if ( !this.variableType.equals( DataType.TYPE_COMPARABLE ) ) {
                return false;
            }
            String[] dd = this.modeller.getSuggestionCompletions().getEnumValues( boundFactType,
                                                                                  this.value.getField() );
            return isEnumEquivalent( dd );
        }

        //If the types are identical (and not SuggestionCompletionEngine.TYPE_COMPARABLE) then return true
        if ( boundFactType.equals( this.variableType ) ) {
            return true;
        }
        return false;
    }

    private boolean isLHSFieldTypeEquivalent( String boundVariable ) {
        String boundFieldType = modeller.getModel().getLHSBindingType( boundVariable );

        //If the fieldTypes are SuggestionCompletionEngine.TYPE_COMPARABLE check the enums are equivalent
        if ( boundFieldType.equals( DataType.TYPE_COMPARABLE ) ) {
            if ( !this.variableType.equals( DataType.TYPE_COMPARABLE ) ) {
                return false;
            }
            FieldConstraint fc = this.modeller.getModel().getLHSBoundField( boundVariable );
            if ( fc instanceof SingleFieldConstraint ) {
                String fieldName = ( (SingleFieldConstraint) fc ).getFieldName();
                String parentFactTypeForBinding = this.modeller.getModel().getLHSParentFactPatternForBinding( boundVariable ).getFactType();
                String[] dd = this.modeller.getSuggestionCompletions().getEnumValues( parentFactTypeForBinding,
                                                                                      fieldName );
                return isEnumEquivalent( dd );
            }
            return false;
        }

        //If the fieldTypes are identical (and not SuggestionCompletionEngine.TYPE_COMPARABLE) then return true
        if ( boundFieldType.equals( this.variableType ) ) {
            return true;
        }
        return false;
    }

    private boolean isRHSFieldTypeEquivalent( String boundVariable ) {
        String boundFactType = modeller.getModel().getRHSBoundFact( boundVariable ).getFactType();
        if ( boundFactType == null ) {
            return false;
        }
        if ( this.variableType == null ) {
            return false;
        }

        //If the types are SuggestionCompletionEngine.TYPE_COMPARABLE check the enums are equivalent
        if ( boundFactType.equals( DataType.TYPE_COMPARABLE ) ) {
            if ( !this.variableType.equals( DataType.TYPE_COMPARABLE ) ) {
                return false;
            }
            String[] dd = this.modeller.getSuggestionCompletions().getEnumValues( boundFactType,
                                                                                  this.value.getField() );
            return isEnumEquivalent( dd );
        }

        //If the types are identical (and not SuggestionCompletionEngine.TYPE_COMPARABLE) then return true
        if ( boundFactType.equals( this.variableType ) ) {
            return true;
        }
        return false;
    }

    private boolean isEnumEquivalent( String[] values ) {
        if ( values == null && this.enums.fixedList != null ) {
            return false;
        }
        if ( values != null && this.enums.fixedList == null ) {
            return false;
        }
        if ( values.length != this.enums.fixedList.length ) {
            return false;
        }
        for ( int i = 0; i < values.length; i++ ) {
            if ( !values[ i ].equals( this.enums.fixedList[ i ] ) ) {
                return false;
            }
        }
        return true;
    }

    private Widget widgets( Button lit,
                            InfoPopup popup ) {
        HorizontalPanel h = new HorizontalPanel();
        h.add( lit );
        h.add( popup );
        return h;
    }

    private void executeOnChangeCommand() {
        if ( this.onChangeCommand != null ) {
            this.onChangeCommand.execute();
        }
    }

    public Command getOnChangeCommand() {
        return onChangeCommand;
    }

    public void setOnChangeCommand( Command onChangeCommand ) {
        this.onChangeCommand = onChangeCommand;
    }

    //Signal (potential) change in Template variables
    private void executeOnTemplateVariablesChange() {
        TemplateVariablesChangedEvent tvce = new TemplateVariablesChangedEvent( model );
        eventBus.fireEventFromSource( tvce,
                                      model );
    }

}
