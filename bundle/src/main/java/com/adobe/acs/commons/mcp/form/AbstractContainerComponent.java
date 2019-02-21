/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2017 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.adobe.acs.commons.mcp.form;

import com.adobe.acs.commons.mcp.util.AnnotatedFieldDeserializer;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;

/**
 * Represent a generic container component which has one or more children
 */
public class AbstractContainerComponent extends FieldComponent {

    Map<String, FieldComponent> fieldComponents = new LinkedHashMap<>();
    private boolean composite;
    private AbstractGroupingContainerComponent groupingContainer;
    private Class<? extends FieldComponent> defaultChildComponent = TextfieldComponent.class;

    @Override
    public void init() {
        if (getField() != null) {
            if (getField().getType().isArray()) {
                extractFieldComponents(getField().getType().getComponentType());
            } else if (Collection.class.isAssignableFrom(getField().getType())) {
                ParameterizedType type = (ParameterizedType) getField().getGenericType();
                Class clazz = (Class) type.getActualTypeArguments()[0];
                extractFieldComponents(clazz);
            } else {
                extractFieldComponents(getField().getType());
                fieldComponents.values().forEach(comp -> {
                    ResourceMetadata meta = comp.getComponentMetadata();
                    String currentName = String.valueOf(meta.get("name"));
                    meta.put("name", getField().getName() + "/" + currentName);
                });
            }
        }
        if (getHelper() != null) {
            setPath(getHelper().getRequest().getResource().getPath());
        }
    }

    public AbstractGroupingContainerComponent getGroupingContainer() {
        if (groupingContainer == null) {
            groupingContainer = new AbstractGroupingContainerComponent.AccordionComponent();
        }
        return groupingContainer;
    }

    public void setGroupingContainer(AbstractGroupingContainerComponent comp) {
        groupingContainer = comp;
    }

    public Map<String, FieldComponent> getFieldComponents() {
        return fieldComponents;
    }

    public FieldComponent generateDefaultChildComponent() {
        try {
            return defaultChildComponent.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(AbstractContainerComponent.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    private void extractFieldComponents(Class clazz) {
        if (clazz == String.class || clazz.isPrimitive()) {
            FieldComponent comp = generateDefaultChildComponent();
            FormField fieldDef = FormField.Factory.create(getName(), "", null, null, false, comp.getClass(), null);
            comp.setup(getName(), null, fieldDef, getHelper());
            comp.getComponentMetadata().put("title", getName());
            // TODO: Provide a proper mechanism for setting path when creating components
            addComponent(getName(), comp);
            composite = false;
        } else {
            AnnotatedFieldDeserializer.getFormFields(clazz, getHelper()).forEach((name, component) -> addComponent(name, component));
            composite = true;
        }
        fieldComponents.values().forEach(this::addClientLibraries);
    }

    public void addComponent(String name, FieldComponent field) {
        fieldComponents.put(name, field);
        addClientLibraries(field);
    }

    protected AbstractResourceImpl generateItemsResource(String path, boolean useFieldSet) {
        AbstractResourceImpl items = new AbstractResourceImpl(path + "/items", "", "", new ResourceMetadata());
        if (hasCategories(fieldComponents.values())) {
            AbstractGroupingContainerComponent groups = getGroupingContainer();
            groups.setPath(path + "/tabs");
            fieldComponents.forEach((name, component) -> groups.addComponent(component.getCategory(), name, component));
            items.addChild(groups.buildComponentResource());
        } else if (useFieldSet) {
            FieldsetComponent fieldset = new FieldsetComponent();
            fieldComponents.forEach((name, comp) -> fieldset.addComponent(name, comp));
            fieldset.setPath(path + "/fields");
            fieldset.setHelper(getHelper());
            items.addChild(fieldset.buildComponentResource());
        } else {
            for (FieldComponent component : fieldComponents.values()) {
                if (getHelper() != null) {
                    component.setHelper(getHelper());
                }
                component.setPath(path + "/items/" + component.getName());
                Resource child = component.buildComponentResource();
                items.addChild(child);
            }
        }
        if (getHelper() != null) {
            items.setResourceResolver(getHelper().getRequest().getResourceResolver());
        }
        return items;
    }

    /**
     * @return the composite
     */
    public boolean isComposite() {
        return composite;
    }

    private boolean hasCategories(Collection<FieldComponent> values) {
        return values.stream()
                .map(FieldComponent::getCategory)
                .filter(s -> s != null && !s.isEmpty())
                .distinct()
                .count() > 1;
    }

    /**
     * @param defaultChildComponent the defaultChildComponent to set
     */
    public void setDefaultChildComponent(Class<? extends FieldComponent> defaultChildComponent) {
        this.defaultChildComponent = defaultChildComponent;
    }
}
