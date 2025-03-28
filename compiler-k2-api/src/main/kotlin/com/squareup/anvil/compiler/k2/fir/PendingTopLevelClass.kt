package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class PendingTopLevelClass(
  public val classId: ClassId,
  override val key: GeneratedDeclarationKey,
  override val classKind: ClassKind,
  override val visibility: Visibility,
  firExtension: FirExtension,
  override val constructors: (context: MemberGenerationContext) -> List<FirConstructor> = { emptyList() },
  override val annotations: FirLazyValue<List<FirAnnotation>> = firExtension.session.firCachesFactory.createLazyValue { emptyList() },
  override val supertypes: FirLazyValue<List<ConeKotlinType>> = firExtension.session.firCachesFactory.createLazyValue { emptyList() },
  nestedClasses: NestedClassLikeDeclarationFactory = NestedClassLikeDeclarationFactory { emptyList() },
  members: MemberCallableDeclarationFactory = MemberCallableDeclarationFactory { emptyList() },
) : PendingClassLikeDeclaration(firExtension.session) {

  public val generatedClass: FirLazyValue<FirRegularClass> = cachesFactory.createLazyValue {
    firExtension.createTopLevelClass(
      classId = classId,
      key = key,
      classKind = classKind,
    ) {
      visibility = this@PendingTopLevelClass.visibility
      supertypes.getValue().forEach { superType(it) }
    }.apply {
      replaceAnnotations(this@PendingTopLevelClass.annotations.getValue())
    }
  }
  override val members: List<PendingMemberCallable> by cachesFactory.createLazyValue {
    members.create(generatedClass.getValue().symbol)
  }
  override val nestedClasses: List<PendingNestedClassLikeDeclaration> by cachesFactory.createLazyValue {
    nestedClasses.create(generatedClass.getValue().symbol)
  }
}

public sealed class PendingClassLikeDeclaration(
  session: FirSession,
) : HasFirCachesFactory(session.firCachesFactory) {
  public abstract val key: GeneratedDeclarationKey
  public abstract val classKind: ClassKind
  public abstract val visibility: Visibility
  public abstract val supertypes: FirLazyValue<List<ConeKotlinType>>
  public abstract val annotations: FirLazyValue<List<FirAnnotation>>
  public abstract val members: List<PendingMemberCallable>
  public abstract val constructors: (context: MemberGenerationContext) -> List<FirConstructor>
  public abstract val nestedClasses: List<PendingNestedClassLikeDeclaration>
}

public sealed class PendingNestedClassLikeDeclaration(session: FirSession) :
  PendingClassLikeDeclaration(session) {
  public abstract val ownerSymbol: FirLazyValue<FirClassSymbol<*>>
  public abstract val name: Name
  public abstract val generatedClass: FirLazyValue<FirRegularClass>
}

public class PendingNestedClass(
  override val name: Name,
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  override val classKind: ClassKind,
  override val visibility: Visibility,
  override val annotations: FirLazyValue<List<FirAnnotation>>,
  firExtension: FirExtension,
  members: MemberCallableDeclarationFactory = MemberCallableDeclarationFactory { emptyList() },
  override val constructors: (context: MemberGenerationContext) -> List<FirConstructor> = { emptyList() },
  override val supertypes: FirLazyValue<List<ConeKotlinType>> = firExtension.session.firCachesFactory.createLazyValue { emptyList() },
  nestedClasses: NestedClassLikeDeclarationFactory = NestedClassLikeDeclarationFactory { emptyList() },
) : PendingNestedClassLikeDeclaration(firExtension.session) {

  override val generatedClass: FirLazyValue<FirRegularClass> =
    cachesFactory.createLazyValue {
      firExtension.createNestedClass(owner = ownerSymbol.getValue(), name, key = key, classKind) {
        this@createNestedClass.visibility = this@PendingNestedClass.visibility
      }
        .apply {
          replaceAnnotations(this@PendingNestedClass.annotations.getValue())
        }
    }

  override val members: List<PendingMemberCallable> by cachesFactory.createLazyValue {
    members.create(generatedClass.getValue().symbol)
  }
  override val nestedClasses: List<PendingNestedClassLikeDeclaration> by cachesFactory.createLazyValue {
    nestedClasses.create(generatedClass.getValue().symbol)
  }
}

public class PendingCompanionObject(
  override val ownerSymbol: FirLazyValue<FirClassSymbol<*>>,
  override val key: GeneratedDeclarationKey,
  override val visibility: Visibility,
  override val annotations: FirLazyValue<List<FirAnnotation>>,
  firExtension: FirExtension,
  members: MemberCallableDeclarationFactory = MemberCallableDeclarationFactory { emptyList() },
  override val supertypes: FirLazyValue<List<ConeKotlinType>> = firExtension.session.firCachesFactory.createLazyValue { emptyList() },
  nestedClasses: NestedClassLikeDeclarationFactory = NestedClassLikeDeclarationFactory { emptyList() },
) : PendingNestedClassLikeDeclaration(firExtension.session) {

  override val constructors: (MemberGenerationContext) -> List<FirConstructor> = { ctx ->
    listOf(firExtension.createDefaultPrivateConstructor(owner = ctx.owner, key = key))
  }

  override val name: Name = SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
  override val classKind: ClassKind = ClassKind.OBJECT

  override val generatedClass: FirLazyValue<FirRegularClass> = ownerSymbol.map { ownerSymbol ->

    firExtension.createCompanionObject(owner = ownerSymbol, key = key) {
      this@createCompanionObject.visibility = this@PendingCompanionObject.visibility
    }
      .apply {
        replaceAnnotations(this@PendingCompanionObject.annotations.getValue())
      }
  }

  override val members: List<PendingMemberCallable> by generatedClass.map { members.create(it.symbol) }

  override val nestedClasses: List<PendingNestedClassLikeDeclaration>
    by generatedClass.map { nestedClasses.create(it.symbol) }
}

public typealias NestedClassLikeDeclarationFactory = OwnedPendingDeclarationFactory<FirClassSymbol<*>, PendingNestedClassLikeDeclaration>
public typealias MemberCallableDeclarationFactory = OwnedPendingDeclarationFactory<FirClassSymbol<*>, PendingMemberCallable>

@NestedOwnerDSL
public fun interface OwnedPendingDeclarationFactory<OWNER : FirBasedSymbol<*>, R> {
  public fun create(owner: OWNER): List<R>
}

@DslMarker
internal annotation class NestedOwnerDSL
