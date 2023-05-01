/*******************************************************************************
 * Copyright (c) 2010-present Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Stuart McCulloch (Sonatype, Inc.) - initial API and implementation
 *******************************************************************************/
package com.walmartlabs.concord.runtime.v2.runner.guice;

import org.eclipse.sisu.inject.Logs;
import org.eclipse.sisu.space.*;
import org.eclipse.sisu.space.asm.ClassReader;
import org.eclipse.sisu.space.asm.Opcodes;
import org.eclipse.sisu.space.asm.Type;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Enumeration;

/**
 * Makes a {@link SpaceVisitor} visit a {@link ClassSpace}; can be directed by an optional {@link ClassFinder}.
 */
public final class SpaceScanner_
{
    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    private static final int ASM_FLAGS = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    static final ClassFinder DEFAULT_FINDER = new DefaultClassFinder();

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private final ClassSpace space;

    private final ClassFinder finder;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public SpaceScanner_(final ClassSpace space, final ClassFinder finder )
    {
        this.space = space;
        this.finder = finder;
    }

    public SpaceScanner_(final ClassSpace space )
    {
        this( space, DEFAULT_FINDER );
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    /**
     * Makes the given {@link SpaceVisitor} visit the {@link ClassSpace} of this scanner.
     * 
     * @param visitor The class space visitor
     */
    public void accept( final SpaceVisitor visitor )
    {
        visitor.enterSpace( space );

        for ( final Enumeration<URL> result = finder.findClasses( space ); result.hasMoreElements(); )
        {
            final URL url = result.nextElement();
            final ClassVisitor cv = visitor.visitClass( url );
            if ( null != cv )
            {
                accept( cv, url );
            }
        }

        visitor.leaveSpace();
    }

    /**
     * Makes the given {@link ClassVisitor} visit the class contained in the resource {@link URL}.
     * 
     * @param visitor The class space visitor
     * @param url The class resource URL
     */
    public static void accept( final ClassVisitor visitor, final URL url )
    {
        if ( null == url )
        {
            return; // nothing to visit
        }
        try
        {
            final InputStream in = Streams.open( url );
            try
            {
                new ClassReader( in ).accept( adapt( visitor ), ASM_FLAGS );
            }
            finally
            {
                in.close();
            }
        }
        catch ( final ArrayIndexOutOfBoundsException e ) // NOPMD
        {
            // ignore broken class constant pool in icu4j
        }
        catch ( final Exception e )
        {
            Logs.debug( "Problem scanning: {}", url, e );
        }
    }

    /**
     * Returns the JVM descriptor for the given annotation class, such as "Ljavax/inject/Qualifier;".
     * 
     * @param clazz The annotation class
     * @return JVM descriptor of the class
     * @see ClassVisitor#visitAnnotation(String)
     */
    public static String jvmDescriptor( final Class<? extends Annotation> clazz )
    {
        return 'L' + clazz.getName().replace( '.', '/' ) + ';';
    }

    // ----------------------------------------------------------------------
    // Implementation methods
    // ----------------------------------------------------------------------

    /**
     * Adapts the given {@link ClassVisitor} to its equivalent ASM form.
     * 
     * @param _cv The class visitor to adapt
     * @return ASM-compatible class visitor
     */
    private static org.eclipse.sisu.space.asm.ClassVisitor adapt( final ClassVisitor _cv )
    {
        return null == _cv ? null : new org.eclipse.sisu.space.asm.ClassVisitor( Opcodes.ASM9 )
        {
            @Override
            public void visit( final int version, final int access, final String name, final String signature,
                               final String superName, final String[] interfaces )
            {
                _cv.enterClass( access, name, superName, interfaces );
            }

            @Override
            public org.eclipse.sisu.space.asm.AnnotationVisitor visitAnnotation( final String desc,
                                                                                 final boolean visible )
            {
                final AnnotationVisitor _av = _cv.visitAnnotation( desc );
                return null == _av ? null : new org.eclipse.sisu.space.asm.AnnotationVisitor( Opcodes.ASM9 )
                {
                    {
                        _av.enterAnnotation();
                    }

                    @Override
                    public void visit( final String name, final Object value )
                    {
                        _av.visitElement( name, value instanceof Type ? ( (Type) value ).getClassName() : value );
                    }

                    @Override
                    public void visitEnd()
                    {
                        _av.leaveAnnotation();
                    }
                };
            }

            @Override
            public void visitEnd()
            {
                _cv.leaveClass();
            }
        };
    }
}
