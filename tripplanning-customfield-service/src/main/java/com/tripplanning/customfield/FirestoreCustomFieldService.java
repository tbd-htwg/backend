package com.tripplanning.customfield;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;

import com.tripplanning.common.tenant.TenantFirestoreCollections;
import com.tripplanning.customfield.dto.CustomFieldDtos.CustomFieldDeclarationDto;
import com.tripplanning.customfield.dto.CustomFieldDtos.TripCustomFieldValueDto;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FirestoreCustomFieldService {

  private final Firestore firestore;

  public List<CustomFieldDeclarationDto> listDeclarations(String tenantSlug, boolean includeArchived) {
    try {
      QuerySnapshot snapshot =
          firestore.collection(TenantFirestoreCollections.customFields(tenantSlug)).get().get();
      List<CustomFieldDeclarationDto> result = new ArrayList<>();
      for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
        CustomFieldDeclarationDto row = toDeclaration(doc);
        if (includeArchived || !row.archived()) {
          result.add(row);
        }
      }
      result.sort(Comparator.comparing(CustomFieldDeclarationDto::id));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      throw firestoreError("list declarations failed", e.getCause());
    }
  }

  public CustomFieldDeclarationDto createDeclaration(
      String tenantSlug, String id, String name, CustomFieldType type) {
    String normalizedId = CustomFieldValidation.normalizeId(id);
    String normalizedName = CustomFieldValidation.normalizeName(name);
    DocumentReference ref =
        firestore
            .collection(TenantFirestoreCollections.customFields(tenantSlug))
            .document(normalizedId);
    try {
      if (ref.get().get().exists()) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Custom field id already exists: " + normalizedId);
      }
      long createdAt = System.currentTimeMillis();
      Map<String, Object> data = new HashMap<>();
      data.put("name", normalizedName);
      data.put("type", type.name());
      data.put("archived", false);
      data.put("createdAt", createdAt);
      ref.set(data).get();
      return new CustomFieldDeclarationDto(normalizedId, normalizedName, type, false, createdAt);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ResponseStatusException rse) {
        throw rse;
      }
      throw firestoreError("create declaration failed", cause);
    }
  }

  public CustomFieldDeclarationDto setArchived(
      String tenantSlug, String fieldId, boolean archived) {
    String normalizedId = CustomFieldValidation.normalizeId(fieldId);
    DocumentReference ref =
        firestore
            .collection(TenantFirestoreCollections.customFields(tenantSlug))
            .document(normalizedId);
    try {
      DocumentSnapshot snapshot = ref.get().get();
      if (!snapshot.exists()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Custom field not found");
      }
      ref.update("archived", archived).get();
      return toDeclaration(snapshot, archived);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ResponseStatusException rse) {
        throw rse;
      }
      throw firestoreError("archive failed", cause);
    }
  }

  public List<TripCustomFieldValueDto> listTripValues(String tenantSlug, long tripId) {
    Map<String, CustomFieldDeclarationDto> declarations = new HashMap<>();
    for (CustomFieldDeclarationDto decl : listDeclarations(tenantSlug, false)) {
      declarations.put(decl.id(), decl);
    }
    if (declarations.isEmpty()) {
      return List.of();
    }
    try {
      QuerySnapshot snapshot =
          firestore
              .collection(TenantFirestoreCollections.tripCustomFieldValues(tenantSlug))
              .whereEqualTo("tripId", tripId)
              .get()
              .get();
      Map<String, String> valuesByFieldId = new HashMap<>();
      for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
        String fieldId = doc.getString("fieldId");
        if (fieldId != null) {
          valuesByFieldId.put(fieldId, doc.getString("value"));
        }
      }
      List<TripCustomFieldValueDto> result = new ArrayList<>();
      for (CustomFieldDeclarationDto decl : declarations.values()) {
        result.add(
            new TripCustomFieldValueDto(
                decl.id(),
                decl.name(),
                decl.type(),
                valuesByFieldId.getOrDefault(decl.id(), "")));
      }
      result.sort(Comparator.comparing(TripCustomFieldValueDto::fieldId));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      throw firestoreError("list trip values failed", e.getCause());
    }
  }

  public List<TripCustomFieldValueDto> upsertTripValues(
      String tenantSlug, long tripId, List<Map.Entry<String, String>> entries) {
    Map<String, CustomFieldDeclarationDto> declarations = new HashMap<>();
    for (CustomFieldDeclarationDto decl : listDeclarations(tenantSlug, false)) {
      declarations.put(decl.id(), decl);
    }
    if (declarations.isEmpty() && !entries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active custom fields defined");
    }
    long updatedAt = System.currentTimeMillis();
    try {
      WriteBatch batch = firestore.batch();
      Map<String, String> persisted = new HashMap<>();
      for (Map.Entry<String, String> entry : entries) {
        String fieldId = CustomFieldValidation.normalizeId(entry.getKey());
        CustomFieldDeclarationDto decl = declarations.get(fieldId);
        if (decl == null) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Unknown or archived custom field: " + fieldId);
        }
        String validated = CustomFieldValidation.validateValue(decl.type(), entry.getValue());
        persisted.put(fieldId, validated);
        DocumentReference ref =
            firestore
                .collection(TenantFirestoreCollections.tripCustomFieldValues(tenantSlug))
                .document(CustomFieldValidation.valueDocumentId(tripId, fieldId));
        if (validated.isEmpty()) {
          batch.delete(ref);
        } else {
          Map<String, Object> data = new HashMap<>();
          data.put("tripId", tripId);
          data.put("fieldId", fieldId);
          data.put("value", validated);
          data.put("updatedAt", updatedAt);
          batch.set(ref, data);
        }
      }
      batch.commit().get();
      List<TripCustomFieldValueDto> result = new ArrayList<>();
      for (CustomFieldDeclarationDto decl : declarations.values()) {
        result.add(
            new TripCustomFieldValueDto(
                decl.id(),
                decl.name(),
                decl.type(),
                persisted.getOrDefault(
                    decl.id(),
                    loadExistingValue(tenantSlug, tripId, decl.id()))));
      }
      result.sort(Comparator.comparing(TripCustomFieldValueDto::fieldId));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ResponseStatusException rse) {
        throw rse;
      }
      throw firestoreError("upsert trip values failed", cause);
    }
  }

  private String loadExistingValue(String tenantSlug, long tripId, String fieldId) {
    try {
      DocumentSnapshot doc =
          firestore
              .collection(TenantFirestoreCollections.tripCustomFieldValues(tenantSlug))
              .document(CustomFieldValidation.valueDocumentId(tripId, fieldId))
              .get()
              .get();
      if (!doc.exists()) {
        return "";
      }
      String value = doc.getString("value");
      return value != null ? value : "";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw firestoreError("interrupted", e);
    } catch (ExecutionException e) {
      throw firestoreError("load value failed", e.getCause());
    }
  }

  private static CustomFieldDeclarationDto toDeclaration(DocumentSnapshot doc) {
    Boolean archived = doc.getBoolean("archived");
    Long createdAt = doc.getLong("createdAt");
    return new CustomFieldDeclarationDto(
        doc.getId(),
        doc.getString("name"),
        CustomFieldType.parse(doc.getString("type")),
        archived != null && archived,
        createdAt != null ? createdAt : 0L);
  }

  private static CustomFieldDeclarationDto toDeclaration(DocumentSnapshot doc, boolean archived) {
    Long createdAt = doc.getLong("createdAt");
    return new CustomFieldDeclarationDto(
        doc.getId(),
        doc.getString("name"),
        CustomFieldType.parse(doc.getString("type")),
        archived,
        createdAt != null ? createdAt : 0L);
  }

  private static ResponseStatusException firestoreError(String message, Throwable cause) {
    String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : message;
    return new ResponseStatusException(
        HttpStatus.INTERNAL_SERVER_ERROR, "Firestore " + message + ": " + detail);
  }
}
