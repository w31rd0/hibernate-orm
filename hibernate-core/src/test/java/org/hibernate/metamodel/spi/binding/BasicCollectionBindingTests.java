/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.spi.binding;

import java.util.Collection;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.hibernate.engine.FetchTiming;
import org.hibernate.metamodel.MetadataSourceProcessingOrder;
import org.hibernate.metamodel.MetadataSources;
import org.hibernate.metamodel.internal.MetadataImpl;
import org.hibernate.metamodel.spi.domain.PluralAttributeNature;
import org.hibernate.metamodel.spi.relational.ForeignKey;
import org.hibernate.metamodel.spi.relational.Identifier;
import org.hibernate.metamodel.spi.relational.TableSpecification;
import org.hibernate.metamodel.spi.relational.Value;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.service.internal.StandardServiceRegistryImpl;
import org.hibernate.testing.junit4.BaseUnitTestCase;
import org.hibernate.type.BagType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.SetType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Steve Ebersole
 */
public class BasicCollectionBindingTests extends BaseUnitTestCase {
	private StandardServiceRegistryImpl serviceRegistry;

	@Before
	public void setUp() {
		serviceRegistry = (StandardServiceRegistryImpl) new ServiceRegistryBuilder().buildServiceRegistry();
	}

	@After
	public void tearDown() {
		serviceRegistry.destroy();
	}

//	@Test
//	public void testAnnotations() {
//		doTest( MetadataSourceProcessingOrder.ANNOTATIONS_FIRST );
//	}

	@Test
	public void testHbm() {
		doTest( MetadataSourceProcessingOrder.HBM_FIRST );
	}

	private void doTest(MetadataSourceProcessingOrder processingOrder) {
		MetadataSources sources = new MetadataSources( serviceRegistry );
//		sources.addAnnotatedClass( EntityWithBasicCollections.class );
		sources.addResource( "org/hibernate/metamodel/spi/binding/EntityWithBasicCollections.hbm.xml" );
		MetadataImpl metadata = (MetadataImpl) sources.getMetadataBuilder().with( processingOrder ).buildMetadata();

		final EntityBinding entityBinding = metadata.getEntityBinding( EntityWithBasicCollections.class.getName() );
		final EntityIdentifier entityIdentifier = entityBinding.getHierarchyDetails().getEntityIdentifier();
		assertNotNull( entityBinding );

		// TODO: this will fail until HHH-7121 is fixed
		//assertTrue( entityBinding.getPrimaryTable().locateColumn( "`name`" ).isUnique() );

		checkResult(
				entityBinding,
				metadata.getCollection( EntityWithBasicCollections.class.getName() + ".theBag" ),
				BagType.class,
				Collection.class,
				String.class,
				entityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding(),
				Identifier.toIdentifier( "EntityWithBasicCollections_theBag" ),
				Identifier.toIdentifier( "owner_id" ),
				FetchTiming.IMMEDIATE,
				true
		);

		checkResult(
				entityBinding,
				metadata.getCollection( EntityWithBasicCollections.class.getName() + ".theSet" ),
				SetType.class,
				Set.class,
				String.class,
				entityBinding.getHierarchyDetails().getEntityIdentifier().getAttributeBinding(),
				Identifier.toIdentifier( "EntityWithBasicCollections_theSet" ),
				Identifier.toIdentifier( "pid" ),
				FetchTiming.EXTRA_DELAYED,
				true
		);

		checkResult(
				entityBinding,
				metadata.getCollection( EntityWithBasicCollections.class.getName() + ".thePropertyRefSet" ),
				SetType.class,
				Set.class,
				Integer.class,
				(SingularAttributeBinding) entityBinding.locateAttributeBinding( "name" ),
				Identifier.toIdentifier( "EntityWithBasicCollections_thePropertyRefSet" ),
				Identifier.toIdentifier( "pid" ),
				FetchTiming.DELAYED,
				false
		);
	}

	private <X extends CollectionType> void checkResult(
			EntityBinding collectionOwnerBinding,
			PluralAttributeBinding collectionBinding,
			Class<X> expectedCollectionTypeClass,
			Class<?> expectedCollectionJavaClass,
			Class<?> expectedElementJavaClass,
			SingularAttributeBinding expectedKeyTargetAttributeBinding,
			Identifier expectedCollectionTableName,
			Identifier expectedKeySourceColumnName,
			FetchTiming expectedFetchTiming,
			boolean expectedElementNullable) {
		assertNotNull( collectionBinding );
		assertSame(
				collectionBinding,
				collectionOwnerBinding.locateAttributeBinding( collectionBinding.getAttribute().getName() )
		);
		assertSame( collectionOwnerBinding, collectionBinding.getContainer().seekEntityBinding() );

		TableSpecification collectionTable =  collectionBinding.getPluralAttributeKeyBinding().getCollectionTable();
		assertNotNull( collectionTable );
		assertEquals( expectedCollectionTableName, collectionTable.getLogicalName() );
		PluralAttributeKeyBinding keyBinding = collectionBinding.getPluralAttributeKeyBinding();
		assertSame( collectionBinding, keyBinding.getPluralAttributeBinding() );
		HibernateTypeDescriptor collectionHibernateTypeDescriptor = collectionBinding.getHibernateTypeDescriptor();
		assertNull( collectionHibernateTypeDescriptor.getExplicitTypeName() );
		assertEquals( expectedCollectionJavaClass.getName(), collectionHibernateTypeDescriptor.getJavaTypeName() );
		assertTrue( collectionHibernateTypeDescriptor.getTypeParameters().isEmpty() );
		assertTrue( expectedCollectionTypeClass.isInstance( collectionHibernateTypeDescriptor.getResolvedTypeMapping() ) );
		assertFalse( collectionHibernateTypeDescriptor.getResolvedTypeMapping().isComponentType() );
		final String role = collectionBinding.getAttribute().getRole();
		assertEquals(
				role,
				collectionOwnerBinding.getEntity().getName() + "." + collectionBinding.getAttribute().getName()
		);
		assertEquals(
				role,
				expectedCollectionTypeClass.cast( collectionHibernateTypeDescriptor.getResolvedTypeMapping() ).getRole()
		);

		assertEquals( expectedFetchTiming, collectionBinding.getFetchTiming() );
		assertEquals( expectedFetchTiming != FetchTiming.IMMEDIATE, collectionBinding.isLazy() );

		ForeignKey fk = keyBinding.getForeignKey();
		assertNotNull( fk );
		assertSame( collectionTable, fk.getSourceTable() );
		assertEquals( 1, fk.getColumnSpan() );
		assertEquals( 1, expectedKeyTargetAttributeBinding.getRelationalValueBindings().size() );
		Value expectedFKTargetValue = expectedKeyTargetAttributeBinding.getRelationalValueBindings().get( 0 ).getValue();
		assertEquals( fk.getColumns(), fk.getSourceColumns() );
		assertEquals( 1, fk.getSourceColumns().size() );
		assertEquals( 1, fk.getTargetColumns().size() );
		assertEquals( expectedKeySourceColumnName, fk.getSourceColumns().get( 0 ).getColumnName() );
		assertSame( expectedFKTargetValue, fk.getTargetColumns().get( 0 ) );
		assertSame( collectionOwnerBinding.getPrimaryTable(), fk.getTargetTable() );
		assertEquals( expectedFKTargetValue.getJdbcDataType(),  fk.getSourceColumns().get( 0 ).getJdbcDataType() );

		assertSame( ForeignKey.ReferentialAction.NO_ACTION, fk.getDeleteRule() );
		assertSame( ForeignKey.ReferentialAction.NO_ACTION, fk.getUpdateRule() );
		// FK name is null because no default FK name is generated until HHH-7092 is fixed
		assertNull( fk.getName() );
		checkEquals(
				expectedKeyTargetAttributeBinding.getHibernateTypeDescriptor(),
				keyBinding.getHibernateTypeDescriptor()
		);
		assertFalse( keyBinding.isInverse() );
		assertEquals(
				PluralAttributeElementNature.BASIC,
				collectionBinding.getPluralAttributeElementBinding().getPluralAttributeElementNature()
		);
		assertEquals(
				expectedElementJavaClass.getName(),
				collectionBinding.getPluralAttributeElementBinding().getHibernateTypeDescriptor().getJavaTypeName()
		);
		assertEquals(
				expectedElementJavaClass,
				collectionBinding.getPluralAttributeElementBinding().getHibernateTypeDescriptor().getResolvedTypeMapping().getReturnedClass()

		);
		assertEquals( 1, collectionBinding.getPluralAttributeElementBinding().getRelationalValueBindings().size() );
		RelationalValueBinding elementRelationalValueBinding = collectionBinding.getPluralAttributeElementBinding().getRelationalValueBindings().get( 0 );
		assertEquals( expectedElementNullable, elementRelationalValueBinding.isNullable() );
		if ( collectionBinding.getAttribute().getNature() == PluralAttributeNature.BAG ) {
			assertEquals( 0, collectionTable.getPrimaryKey().getColumnSpan() );
		}
		else if ( collectionBinding.getAttribute().getNature() == PluralAttributeNature.SET ) {
			if ( expectedElementNullable ) {
				assertEquals( 0, collectionTable.getPrimaryKey().getColumnSpan() );
			}
			else {
				assertEquals( 2, collectionTable.getPrimaryKey().getColumnSpan() );
				assertSame( fk.getSourceColumns().get( 0 ), collectionTable.getPrimaryKey().getColumns().get( 0 ) );
				assertSame( elementRelationalValueBinding.getValue(),  collectionTable.getPrimaryKey().getColumns().get( 1 ) );
			}
		}
	}

	private void checkEquals(HibernateTypeDescriptor expected, HibernateTypeDescriptor actual) {
		assertEquals( expected.getExplicitTypeName(), actual.getExplicitTypeName() );
		assertEquals( expected.getJavaTypeName(), actual.getJavaTypeName() );
		assertEquals( expected.getTypeParameters(), actual.getTypeParameters() );
		assertEquals( expected.getResolvedTypeMapping(), actual.getResolvedTypeMapping() );
		assertEquals( expected.isToOne(), actual.isToOne() );
	}
}