package com.ytjojo.databind.compiler.annotationprocessor.util;

import com.databinding.tool.util.L;
import com.ytjojo.databind.compiler.annotationprocessor.BindingProcesor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Created by jiulongteng on 2018/7/24.
 */

public class ElementUtils {

    public static HashSet<Element> getAllChilcElements(TypeElement element){
        HashSet<Element> arrayList = new HashSet<>();

        arrayList.addAll(BindingProcesor.sElements.getAllMembers(element));
        TypeMirror superTypeMirror= null;
        TypeElement superTypeElement = null;
        superTypeMirror = element.getSuperclass();
        while (superTypeMirror != null){
            if(superTypeMirror.getKind() == TypeKind.NONE){
                break;
            }
            Element resovled =  BindingProcesor.sTypes.asElement(superTypeMirror);
            if(resovled !=null && resovled instanceof TypeElement){
                superTypeElement = (TypeElement) resovled;
                arrayList.addAll(BindingProcesor.sElements.getAllMembers(element));
                superTypeMirror = superTypeElement.getSuperclass();
            }else {
                break;
            }

        }
        return arrayList;
    }

    public static boolean isInSamePackage(Element elementToCheck, Element original){
        return BindingProcesor.sElements.getPackageOf(elementToCheck).toString().equals(
                BindingProcesor.sElements.getPackageOf(original).toString());
    }

    public static boolean  isPublic(Element element){
        return element.getKind().isField() &&
                !element.getModifiers().contains(Modifier.PUBLIC) ;
    }

    public String getPropertyName(Element element) {
        switch (element.getKind()) {
            case FIELD:
                return stripPrefixFromField((VariableElement) element);
            case METHOD:
                return stripPrefixFromMethod((ExecutableElement) element);
            default:
                L.e("@Bindable is not allowed on %s", element.getKind());
                return null;
        }
    }

    public static String stripPrefixFromField(VariableElement element) {
        Name name = element.getSimpleName();
        if (name.length() >= 2) {
            char firstChar = name.charAt(0);
            char secondChar = name.charAt(1);
            if (name.length() > 2 && firstChar == 'm' && secondChar == '_') {
                char thirdChar = name.charAt(2);
                if (Character.isJavaIdentifierStart(thirdChar)) {
                    return "" + Character.toLowerCase(thirdChar) +
                            name.subSequence(3, name.length());
                }
            } else if ((firstChar == 'm' && Character.isUpperCase(secondChar)) ||
                    (firstChar == '_' && Character.isJavaIdentifierStart(secondChar))) {
                return "" + Character.toLowerCase(secondChar) + name.subSequence(2, name.length());
            }
        }
        return name.toString();
    }

    public String stripPrefixFromMethod(ExecutableElement element) {
        Name name = element.getSimpleName();
        CharSequence propertyName;
        if (isGetter(element) || isSetter(element)) {
            propertyName = name.subSequence(3, name.length());
        } else if (isBooleanGetter(element)) {
            propertyName = name.subSequence(2, name.length());
        } else {
            L.e("@Bindable associated with method must follow JavaBeans convention %s", element);
            return null;
        }
        char firstChar = propertyName.charAt(0);
        return "" + Character.toLowerCase(firstChar) +
                propertyName.subSequence(1, propertyName.length());
    }

    public static boolean prefixes(CharSequence sequence, String prefix) {
        boolean prefixes = false;
        if (sequence.length() > prefix.length()) {
            int count = prefix.length();
            prefixes = true;
            for (int i = 0; i < count; i++) {
                if (sequence.charAt(i) != prefix.charAt(i)) {
                    prefixes = false;
                    break;
                }
            }
        }
        return prefixes;
    }

    public static boolean isGetter(ExecutableElement element) {
        Name name = element.getSimpleName();
        return prefixes(name, "get") &&
                Character.isJavaIdentifierStart(name.charAt(3)) &&
                element.getParameters().isEmpty() &&
                element.getReturnType().getKind() != TypeKind.VOID;
    }

    public static boolean isSetter(ExecutableElement element) {
        Name name = element.getSimpleName();
        return prefixes(name, "set") &&
                Character.isJavaIdentifierStart(name.charAt(3)) &&
                element.getParameters().size() == 1 &&
                element.getReturnType().getKind() == TypeKind.VOID;
    }

    public static boolean isBooleanGetter(ExecutableElement element) {
        Name name = element.getSimpleName();
        return prefixes(name, "is") &&
                Character.isJavaIdentifierStart(name.charAt(2)) &&
                element.getParameters().isEmpty() &&
                element.getReturnType().getKind() == TypeKind.BOOLEAN;
    }


    public static boolean isCollectionOrMap(Element element) {
        if (BindingProcesor.sTypes.isAssignable( element.asType(),TypeUtil.collectionElement.asType()) ){
            return true;
        }
        if (BindingProcesor.sTypes.isAssignable( element.asType(),TypeUtil.mapElement.asType()) ){
            return true;
        }
        return false;
    }
    public static boolean isArray(Element element){
        return element.asType().getKind() == TypeKind.ARRAY;
    }

    /** Finds the parent binder type in the supplied set, if any. */
    public static TypeElement findParentType(TypeElement typeElement, Set<TypeElement> parents) {
        TypeMirror type;
        while (true) {
            type = typeElement.getSuperclass();
            if (type.getKind() == TypeKind.NONE) {
                return null;
            }
            typeElement = (TypeElement) ((DeclaredType) type).asElement();
            if (parents.contains(typeElement)) {
                return typeElement;
            }
        }
    }
}
