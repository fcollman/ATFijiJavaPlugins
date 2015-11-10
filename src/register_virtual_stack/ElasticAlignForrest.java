package register_virtual_stack;
/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import mpicbg.ij.SIFT;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.ij.blockmatching.BlockMatching;
import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel2D;
import mpicbg.models.CoordinateTransformMesh;
import mpicbg.models.ErrorStatistic;
import mpicbg.models.HomographyModel2D;
import mpicbg.models.InvertibleCoordinateTransform;
//FCC changed to use trackem2 version of moving least squares
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.MovingLeastSquaresTransform;
//import mpicbg.models.MovingLeastSquaresTransform;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.SimilarityModel2D;
import mpicbg.models.Spring;
import mpicbg.models.SpringMesh;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.Vertex;
import mpicbg.util.Util;

/**
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de>, edited by Forrest Collman forrest.collman@gmail.com 2011
 * @version 0.1a
 */
//FCC refactored the plugin to be ElasticAlignForrest to not overlap with original in FIJI
//this plugin now has the option of saving the transforms it creates
//it also is more intelligent about figuring out the size of the images before it produces the final images, saving time
//I also changed the way it produces those resized images to be consistent with the way that Transform Virtual Stack slices works,
//so as to make that plugin useful for applying these transforms

public class ElasticAlignForrest implements PlugIn, KeyListener
{
	final static private class Triple< A, B, C >
	{
		final public A a;
		final public B b;
		final public C c;
		
		Triple( final A a, final B b, final C c )
		{
			this.a = a;
			this.b = b;
			this.c = c;
		}
	}
	
	final static private class Param implements Serializable
	{
		private static final long serialVersionUID = 3816564377727147658L;

		public String outputPath = "";

		final public FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();
		
		/**
		 * Closest/next closest neighbor distance ratio
		 */
		public float rod = 0.92f;
		
		/**
		 * Maximal accepted alignment error in px
		 */
		public float maxEpsilon = 25.0f;
		
		/**
		 * Inlier/candidates ratio
		 */
		public float minInlierRatio = 0.1f;
		
		/**
		 * Minimal absolute number of inliers
		 */
		public int minNumInliers = 7;
		
		/**
		 * Transformation models for choice
		 */
		final static public String[] modelStrings = new String[]{ "Translation", "Rigid", "Similarity", "Affine", "Perspective" };
		public int modelIndex = 1;
		
		/**
		 * Maximal number of consecutive slices for which no model could be found
		 */
		public int maxNumFailures = 3;
		
		public int maxImageSize = 1024;
		public float minR = 0.8f;
		public float maxCurvatureR = 3f;
		public float rodR = 0.8f;
		
		public boolean mask = false;
		
		public int modelIndexOptimize = 1;
		public int maxIterationsOptimize = 1000;
		public int maxPlateauwidthOptimize = 200;
		
		public int resolutionSpringMesh = 16;
		public float stiffnessSpringMesh = 0.1f;
		public float dampSpringMesh = 0.6f;
		public float maxStretchSpringMesh = 2000.0f;
		public int maxIterationsSpringMesh = 1000;
		public int maxPlateauwidthSpringMesh = 200;
		
		public boolean interpolate = true;
		public boolean visualize = true;
		public int resolutionOutput = 128;
		public boolean preAlign = true;
		
		public int maxNumThreads = Runtime.getRuntime().availableProcessors()/2;
		
		public boolean setup()
		{
			DirectoryChooser.setDefaultDirectory( outputPath );
			final DirectoryChooser dc = new DirectoryChooser( "Elastically align stack: Output directory" );
			outputPath = dc.getDirectory();
			if ( outputPath == null )
			{
				outputPath = "";
				return false;
			}
			else
			{
				final File d = new File( p.outputPath );
				if ( d.exists() && d.isDirectory() )
					p.outputPath += "/";
				else
					return false;
			}
			
			final GenericDialog gdOutput = new GenericDialog( "Elastically align stack: Output" );
			
			gdOutput.addCheckbox( "interpolate", p.interpolate );
			gdOutput.addCheckbox( "visualize", p.visualize );
			gdOutput.addNumericField( "resolution :", p.resolutionOutput, 0 );
			gdOutput.addCheckbox("pre-align", p.preAlign);
			
			
			gdOutput.showDialog();
			
			if ( gdOutput.wasCanceled() )
				return false;
			
			p.interpolate = gdOutput.getNextBoolean();
			p.visualize = gdOutput.getNextBoolean();
			p.resolutionOutput = ( int )gdOutput.getNextNumber();
			p.preAlign = gdOutput.getNextBoolean();
			
			
			/* SIFT */
			final GenericDialog gd = new GenericDialog( "Elastically align stack: SIFT parameters" );
			
			SIFT.addFields( gd, sift );
			
			gd.addNumericField( "closest/next_closest_ratio :", p.rod, 2 );
			
			gd.addMessage( "Geometric Consensus Filter:" );
			gd.addNumericField( "maximal_alignment_error :", p.maxEpsilon, 2, 6, "px" );
			gd.addNumericField( "minimal_inlier_ratio :", p.minInlierRatio, 2 );
			gd.addNumericField( "minimal_number_of_inliers :", p.minNumInliers, 0 );
			gd.addChoice( "approximate_transformation :", Param.modelStrings, Param.modelStrings[ p.modelIndex ] );
			
			gd.addMessage( "Matching:" );
			gd.addNumericField( "give_up_after :", p.maxNumFailures, 0, 6, "failures" );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
			
			SIFT.readFields( gd, sift );
			
			p.rod = ( float )gd.getNextNumber();
			
			p.maxEpsilon = ( float )gd.getNextNumber();
			p.minInlierRatio = ( float )gd.getNextNumber();
			p.minNumInliers = ( int )gd.getNextNumber();
			p.modelIndex = gd.getNextChoiceIndex();
			p.maxNumFailures = ( int )gd.getNextNumber();
			
			
			/* Block Matching */
			final GenericDialog gdBlockMatching = new GenericDialog( "Elastically align stack: Block Matching parameters" );
			gdBlockMatching.addMessage( "Block Matching:" );
			gdBlockMatching.addNumericField( "maximal_image_size :", p.maxImageSize, 0, 6, "px" );
			gdBlockMatching.addNumericField( "minimal_PMCC_r :", p.minR, 2 );
			gdBlockMatching.addNumericField( "maximal_curvature_ratio :", p.maxCurvatureR, 2 );
			gdBlockMatching.addNumericField( "maximal_second_best_r/best_r :", p.rodR, 2 );
			gdBlockMatching.addNumericField( "resolution :", p.resolutionSpringMesh, 0 );
			gdBlockMatching.addCheckbox( "green_mask_(TODO_more_colors)", mask );
			
			gdBlockMatching.showDialog();
			
			if ( gdBlockMatching.wasCanceled() )
				return false;
			
			p.maxImageSize = ( int )gdBlockMatching.getNextNumber();
			p.minR = ( float )gdBlockMatching.getNextNumber();
			p.maxCurvatureR = ( float )gdBlockMatching.getNextNumber();
			p.rodR = ( float )gdBlockMatching.getNextNumber();
			p.resolutionSpringMesh = ( int )gdBlockMatching.getNextNumber();
			p.mask = gdBlockMatching.getNextBoolean();
			
			/* Optimization */
			final GenericDialog gdOptimize = new GenericDialog( "Elastically align stack: Optimization" );
			
			gdOptimize.addMessage( "Approximate Optimizer:" );
			gdOptimize.addChoice( "approximate_transformation :", Param.modelStrings, Param.modelStrings[ p.modelIndexOptimize ] );
			gdOptimize.addNumericField( "maximal_iterations :", p.maxIterationsOptimize, 0 );
			gdOptimize.addNumericField( "maximal_plateauwidth :", p.maxPlateauwidthOptimize, 0 );
			
			gdOptimize.addMessage( "Spring Mesh:" );
			gdOptimize.addNumericField( "stiffness :", p.stiffnessSpringMesh, 2 );
			gdOptimize.addNumericField( "maximal_stretch :", p.maxStretchSpringMesh, 2, 6, "px" );
			gdOptimize.addNumericField( "maximal_iterations :", p.maxIterationsSpringMesh, 0 );
			gdOptimize.addNumericField( "maximal_plateauwidth :", p.maxPlateauwidthSpringMesh, 0 );
			
			gdOptimize.showDialog();
			
			if ( gdOptimize.wasCanceled() )
				return false;
			
			p.modelIndexOptimize = gdOptimize.getNextChoiceIndex();
			p.maxIterationsOptimize = ( int )gdOptimize.getNextNumber();
			p.maxPlateauwidthOptimize = ( int )gdOptimize.getNextNumber();
			
			p.stiffnessSpringMesh = ( float )gdOptimize.getNextNumber();
			p.maxStretchSpringMesh = ( float )gdOptimize.getNextNumber();
			p.maxIterationsSpringMesh = ( int )gdOptimize.getNextNumber();
			p.maxPlateauwidthSpringMesh = ( int )gdOptimize.getNextNumber();
			
			return true;
		}
		
		public boolean equalSiftPointMatchParams( final Param param )
		{
			return sift.equals( param.sift )
			    && maxEpsilon == param.maxEpsilon
			    && minInlierRatio == param.minInlierRatio
			    && minNumInliers == param.minNumInliers
			    && modelIndex == param.modelIndex;
		}
	}
	
	final static Param p = new Param(); 

	final public void run( final String args )
	{
		if ( IJ.versionLessThan( "1.41n" ) ) return;
		try { run(); }
		catch ( Throwable t ) { t.printStackTrace(); }
	}
	
	final public void run() throws Exception
	{
		final ImagePlus imp = WindowManager.getCurrentImage();
		if ( imp == null )  { System.err.println( "There are no images open" ); return; }
		
		if ( !p.setup() ) return;
		
		final ImageStack stack = imp.getStack();
		final double displayRangeMin = imp.getDisplayRangeMin();
		final double displayRangeMax = imp.getDisplayRangeMax();
		
		final ArrayList< Tile< ? > > tiles = new ArrayList< Tile<?> >();
		for ( int i = 0; i < stack.getSize(); ++i )
		{
			switch ( p.modelIndexOptimize )
			{
			case 0:
				tiles.add( new Tile< TranslationModel2D >( new TranslationModel2D() ) );
				break;
			case 1:
				tiles.add( new Tile< RigidModel2D >( new RigidModel2D() ) );
				break;
			case 2:
				tiles.add( new Tile< SimilarityModel2D >( new SimilarityModel2D() ) );
				break;
			case 3:
				tiles.add( new Tile< AffineModel2D >( new AffineModel2D() ) );
				break;
			case 4:
				tiles.add( new Tile< HomographyModel2D >( new HomographyModel2D() ) );
				break;
			default:
				return;
			}
		}
		
		final ExecutorService exec = Executors.newFixedThreadPool( p.maxNumThreads );
		
		/* extract features for all slices and store them to disk */
		final AtomicInteger counter = new AtomicInteger( 0 );
		final ArrayList< Future< ArrayList< Feature > > > siftTasks = new ArrayList< Future< ArrayList< Feature > > >();
		
		for ( int i = 1; i <= stack.getSize(); i++ )
		{
			final int slice = i;
			siftTasks.add(
					exec.submit( new Callable< ArrayList< Feature > >()
					{
						public ArrayList< Feature > call()
						{
							IJ.showProgress( counter.getAndIncrement(), stack.getSize() );

							//final String path = p.outputPath + stack.getSliceLabel( slice ) + ".features";
							final String path = p.outputPath + String.format( "%05d", slice - 1 ) + ".features";
							ArrayList< Feature > fs = deserializeFeatures( p.sift, path );
							if ( null == fs )
							{
								final FloatArray2DSIFT sift = new FloatArray2DSIFT( p.sift );
								final SIFT ijSIFT = new SIFT( sift );
								fs = new ArrayList< Feature >();
								final ImageProcessor ip = stack.getProcessor( slice );
								ip.setMinAndMax( displayRangeMin, displayRangeMax );
								ijSIFT.extractFeatures( ip, fs );

								if ( ! serializeFeatures( p.sift, fs, path ) )
								{
									//IJ.log( "FAILED to store serialized features for " + stack.getSliceLabel( slice ) );
									IJ.log( "FAILED to store serialized features for " + String.format( "%05d", slice - 1 ) );
								}
							}
							//IJ.log( fs.size() + " features extracted for slice " + stack.getSliceLabel ( slice ) );
							IJ.log( fs.size() + " features extracted for slice " + String.format( "%05d", slice - 1 ) );
							
							return fs;
						}
					} ) );
		}
		
		/* join */
		for ( Future< ArrayList< Feature > > fu : siftTasks )
			fu.get();
		
		siftTasks.clear();
		exec.shutdown();
		
		
		/* collect all pairs of slices for which a model could be found */
		final ArrayList< Triple< Integer, Integer, AbstractModel< ? > > > pairs = new ArrayList< Triple< Integer, Integer, AbstractModel< ? > > >();
		
		counter.set( 0 );
		
		int numFailures = 0;
		
		for ( int i = 0; i < stack.getSize(); ++i )
		{
			final ArrayList< Thread > threads = new ArrayList< Thread >( p.maxNumThreads );
			
			final int sliceA = i;
			
J:			for ( int j = i + 1; j < stack.getSize(); )
			{
				final int numThreads = Math.min( p.maxNumThreads, stack.getSize() - j );
				final ArrayList< Triple< Integer, Integer, AbstractModel< ? > > > models =
					new ArrayList< Triple< Integer, Integer, AbstractModel< ? > > >( numThreads );
				
				for ( int k = 0; k < numThreads; ++k )
					models.add( null );
				
				for ( int t = 0;  t < p.maxNumThreads && j < stack.getSize(); ++t, ++j )
				{
					final int sliceB = j;
					final int ti = t;
					
					final Thread thread = new Thread()
					{
						public void run()
						{
							IJ.showProgress( counter.getAndIncrement(), stack.getSize() - 1 );
							
							IJ.log( "matching " + sliceB + " -> " + sliceA + "..." );
							
							//String path = p.outputPath + stack.getSliceLabel( slice ) + ".pointmatches";
							String path = p.outputPath + String.format( "%05d", sliceB ) + "-" + String.format( "%05d", sliceA ) + ".pointmatches";
							ArrayList< PointMatch > candidates = deserializePointMatches( p, path );
							
							if ( null == candidates )
							{
								ArrayList< Feature > fs1 = deserializeFeatures( p.sift, p.outputPath + String.format( "%05d", sliceA ) + ".features" );
								ArrayList< Feature > fs2 = deserializeFeatures( p.sift, p.outputPath + String.format( "%05d", sliceB ) + ".features" );
								candidates = new ArrayList< PointMatch >( FloatArray2DSIFT.createMatches( fs2, fs1, p.rod ) );
								
								if ( !serializePointMatches( p, candidates, path ) )
									IJ.log( "Could not store point matches!" );
							}
		
							AbstractModel< ? > model;
							switch ( p.modelIndex )
							{
							case 0:
								model = new TranslationModel2D();
								break;
							case 1:
								model = new RigidModel2D();
								break;
							case 2:
								model = new SimilarityModel2D();
								break;
							case 3:
								model = new AffineModel2D();
								break;
							case 4:
								model = new HomographyModel2D();
								break;
							default:
								return;
							}
							
							final ArrayList< PointMatch > inliers = new ArrayList< PointMatch >();
							
							boolean modelFound;
							try
							{
								modelFound = model.filterRansac(
										candidates,
										inliers,
										1000,
										p.maxEpsilon,
										p.minInlierRatio,
										p.minNumInliers );
							}
							catch ( Exception e )
							{
								modelFound = false;
								System.err.println( e.getMessage() );
							}
		
							if ( modelFound )
							{
								IJ.log( sliceB + " -> " + sliceA + ": " + inliers.size() + " corresponding features with an average displacement of " + PointMatch.meanDistance( inliers ) + "px identified." );
								IJ.log( "Estimated transformation model: " + model );
								models.set( ti, new Triple< Integer, Integer, AbstractModel< ? > >( sliceA, sliceB, model ) );
							}
							else
							{
								IJ.log( sliceB + " -> " + sliceA + ": no correspondences found." );
								return;
							}
						}
					};
					threads.add( thread );
					thread.start();
				}
				
				for ( final Thread thread : threads )
					thread.join();
				
				threads.clear();
				
				/* collect successfully matches pairs and break the search on gaps */
				for ( int t = 0; t < models.size(); ++t )
				{
					final Triple< Integer, Integer, AbstractModel< ? > > pair = models.get( t );
					if ( pair == null )
					{
						if ( ++numFailures > p.maxNumFailures )
							break J;
					}
					else
					{
						numFailures = 0;
						pairs.add( pair );
					}
				}
			}
		}
		
		/* Elastic alignment */
		
		/* Initialization */
		final TileConfiguration initMeshes = new TileConfiguration();
		initMeshes.addTiles( tiles );
		
		final ArrayList< SpringMesh > meshes = new ArrayList< SpringMesh >( stack.getSize() );
		for ( int i = 0; i < stack.getSize(); ++i )
			meshes.add( new SpringMesh( p.resolutionSpringMesh, stack.getWidth(), stack.getHeight(), p.stiffnessSpringMesh, p.maxStretchSpringMesh, p.dampSpringMesh ) );
		
		final int blockRadius = Math.max( 32, stack.getWidth() / p.resolutionSpringMesh / 2 );
		
		/** TODO set this something more than the largest error by the approximate model */
		final int searchRadius = Math.round( p.maxEpsilon );
		
		for ( final Triple< Integer, Integer, AbstractModel< ? > > pair : pairs )
		{
			final SpringMesh m1 = meshes.get( pair.a );
			final SpringMesh m2 = meshes.get( pair.b );

			ArrayList< PointMatch > pm12 = new ArrayList< PointMatch >();
			ArrayList< PointMatch > pm21 = new ArrayList< PointMatch >();

			final Collection< Vertex > v1 = m1.getVertices();
			final Collection< Vertex > v2 = m2.getVertices();

			final FloatProcessor ip1 = ( FloatProcessor )stack.getProcessor( pair.a + 1 ).convertToFloat().duplicate();
			final FloatProcessor ip2 = ( FloatProcessor )stack.getProcessor( pair.b + 1 ).convertToFloat().duplicate();
			final FloatProcessor ip1Mask;
			final FloatProcessor ip2Mask;
			
			if ( imp.getType() == ImagePlus.COLOR_RGB && p.mask )
			{
				ip1Mask = createMask( stack.getProcessor( pair.a + 1 ) );
				ip2Mask = createMask( stack.getProcessor( pair.b + 1 ) );
			}
			else
			{
				ip1Mask = null;
				ip2Mask = null;
			}
			
			BlockMatching.matchByMaximalPMCC(
					ip1,
					ip2,
					ip1Mask,
					ip2Mask,
					Math.min( 1.0f, ( float )p.maxImageSize / ip1.getWidth() ),
					( ( InvertibleCoordinateTransform )pair.c ).createInverse(),
					blockRadius,
					blockRadius,
					searchRadius,
					searchRadius,
					p.minR,
					p.rodR,
					p.maxCurvatureR,
					v1,
					pm12,
					new ErrorStatistic( 1 ) );

			IJ.log( pair.a + " > " + pair.b + ": found " + pm12.size() + " correspondences." );

			/* <visualisation> */
			//			final List< Point > s1 = new ArrayList< Point >();
			//			PointMatch.sourcePoints( pm12, s1 );
			//			final ImagePlus imp1 = new ImagePlus( i + " >", ip1 );
			//			imp1.show();
			//			imp1.setOverlay( BlockMatching.illustrateMatches( pm12 ), Color.yellow, null );
			//			imp1.setRoi( Util.pointsToPointRoi( s1 ) );
			//			imp1.updateAndDraw();
			/* </visualisation> */

			BlockMatching.matchByMaximalPMCC(
					ip2,
					ip1,
					ip2Mask,
					ip1Mask,
					Math.min( 1.0f, ( float )p.maxImageSize / ip1.getWidth() ),
					pair.c,
					blockRadius,
					blockRadius,
					searchRadius,
					searchRadius,
					p.minR,
					p.rodR,
					p.maxCurvatureR,
					v2,
					pm21,
					new ErrorStatistic( 1 ) );

			IJ.log( pair.a + " < " + pair.b + ": found " + pm21.size() + " correspondences." );
					
			/* <visualisation> */
			//			final List< Point > s2 = new ArrayList< Point >();
			//			PointMatch.sourcePoints( pm21, s2 );
			//			final ImagePlus imp2 = new ImagePlus( i + " <", ip2 );
			//			imp2.show();
			//			imp2.setOverlay( BlockMatching.illustrateMatches( pm21 ), Color.yellow, null );
			//			imp2.setRoi( Util.pointsToPointRoi( s2 ) );
			//			imp2.updateAndDraw();
			/* </visualisation> */
			
			final float springConstant  = 1.0f / ( pair.b - pair.a );
			IJ.log( pair.a + " <> " + pair.b + " spring constant = " + springConstant );
	
			for ( final PointMatch pm : pm12 )
			{
				final Vertex p1 = ( Vertex )pm.getP1();
				final Vertex p2 = new Vertex( pm.getP2() );
				p1.addSpring( p2, new Spring( 0, springConstant ) );
				m2.addPassiveVertex( p2 );
			}
		
			for ( final PointMatch pm : pm21 )
			{
				final Vertex p1 = ( Vertex )pm.getP1();
				final Vertex p2 = new Vertex( pm.getP2() );
				p1.addSpring( p2, new Spring( 0, springConstant ) );
				m1.addPassiveVertex( p2 );
			}
			
			final Tile< ? > t1 = tiles.get( pair.a );
			final Tile< ? > t2 = tiles.get( pair.b );
			
			if ( pm12.size() > pair.c.getMinNumMatches() )
				t1.connect( t2, pm12 );
			if ( pm21.size() > pair.c.getMinNumMatches() )
				t2.connect( t1, pm21 );
			IJ.log("loop done");
		}
		
		IJ.log("pre-aligning");
		/* pre-align by optimizing a piecewise linear model */ 
		initMeshes.optimize( p.maxEpsilon, p.maxIterationsSpringMesh, p.maxPlateauwidthSpringMesh );
		for ( int i = 0; i < stack.getSize(); ++i )
			meshes.get( i ).init( tiles.get( i ).getModel() );

		IJ.log("optimizing mesh");
		/* optimize the meshes */
		try
		{
			long t0 = System.currentTimeMillis();
			IJ.log("Optimizing spring meshes...");
			
			SpringMesh.optimizeMeshes( meshes, p.maxEpsilon, p.maxIterationsSpringMesh, p.maxPlateauwidthSpringMesh, p.visualize );

			IJ.log("Done optimizing spring meshes. Took " + (System.currentTimeMillis() - t0) + " ms");
			
		}
		catch ( NotEnoughDataPointsException e ) { e.printStackTrace(); }
		
		/* calculate bounding box */
		final double[] min = new double[ 2 ];
		final double[] max = new double[ 2 ];
		for ( final SpringMesh mesh : meshes )
		{
			final double[] meshMin = new double[ 2 ];
			final double[] meshMax = new double[ 2 ];
			
			mesh.bounds( meshMin, meshMax );
			
			Util.min( min, meshMin );
			Util.max( max, meshMax );
		}
		
		/* translate relative to bounding box */
		for ( final SpringMesh mesh : meshes )
		{
			for ( final Vertex vertex : mesh.getVertices() )
			{
				final double[] w = vertex.getW();
				w[ 0 ] -= min[ 0 ];
				w[ 1 ] -= min[ 1 ];
			}
			mesh.updateAffines();
			mesh.updatePassiveVertices();
		}
		
	
		//IJ.log("about to go through and save transforms");
		
		//loop through all the transforms and fit the moving least squares model to the springmesh
		//also calculate a lower resolution (32) bit mesh transform of the resulting model to figure out the 
		//actual bounds necessary for the transformation, such that reapplication of this mlt transform set
		//will produce images of the exact same size
		//also write the transformation in xml format to the output directory
		
		//reinitialize the bounds to 0,0
		min[0]=0;
		min[1]=0;
		max[0]=0;
		max[1]=0;
		
		//An array to store all the transforms as we calculate them
		final ArrayList< MovingLeastSquaresTransform > transforms = new ArrayList< MovingLeastSquaresTransform >( stack.getSize() );
		for ( int i = 0; i < stack.getSize(); ++i )
		{
			//final int slice  = i + 1;
			
			//initialize the least square model and fit it to the spring mesh
			final MovingLeastSquaresTransform mlt = new MovingLeastSquaresTransform();
			mlt.setModel( AffineModel2D.class );
			mlt.setAlpha( 2.0f );
			mlt.setMatches( meshes.get( i ).getVA().keySet() );
			transforms.add(i, mlt);
			//IJ.log("outputing transform: " + imp.getTitle() + String.format( "%05d", i ));
			
			//write the transformation to an xml file in the output directory using the slice label as a file name
			File file = new File(p.outputPath + imp.getTitle() + String.format( "%05d", i ) + ".xml"); 
			FileOutputStream f = new FileOutputStream(file);  
			PrintWriter out = new PrintWriter(f);
			out.println(mlt.toXML(""));
			out.close();
			f.close();
			
			//calculate the bounding box for this transformation
			final double[] meshMin = new double[ 2 ];
			final double[] meshMax = new double[ 2 ];
			
			//calculate a mesh transform at low res (32) that approximates this mlt transform
			//and figure out if it moves the global bounding box
			final TransformMeshMapping< CoordinateTransformMesh > mapping = new TransformMeshMapping< CoordinateTransformMesh >( new CoordinateTransformMesh( mlt, 32, stack.getWidth(), stack.getHeight() ) );
			mapping.getTransform().bounds(meshMin, meshMax);
			Util.min( min, meshMin );
			Util.max( max, meshMax );
		}
		
		//this is the actual width and height necessary to contain the transform, 
		//and can be recalculated from the mlt transform xml files alone
		final int width = ( int )Math.round( max[ 0 ] - min[ 0 ] );
		final int height = ( int )Math.round( max[ 1 ] - min[ 1 ] );
		
		//output the width and height to a file
		//File fWidthHeight = new File(p.outputPath + "widthheight" + ".txt"); 
		//FileOutputStream fosWidthHeight = new FileOutputStream(fWidthHeight);  
		//PrintWriter pwWidthHeight = new PrintWriter(fosWidthHeight);
		//pwWidthHeight.println(width + " " + height);
		//pwWidthHeight.close();
		//fosWidthHeight.close();
		
		//now loop through the stack again, applying the transformations to each image in a multithreaded fashion
		ExecutorService exe = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		ArrayList<Future<Boolean>> save_job = new ArrayList <Future<Boolean>>();
		for ( int i = 0; i < stack.getSize(); ++i )
		{
			final int slice  = i + 1;
			save_job.add(
					exe.submit(
							applyTransformAndSave(
									stack.getProcessor(slice),
									imp.getTitle() + String.format( "%05d", i ),
									p.outputPath,
									transforms.get(i),
									p.interpolate,
									p.resolutionOutput,
									width,
									height)));
		}
		
		// Wait for the intermediate output files to be saved
		int ind = 0;
		for (Iterator<Future<Boolean>> it = save_job.iterator(); it.hasNext(); )
		{
			ind++;
			Boolean saved_file = null;
			try{
				IJ.showStatus("Applying transform " + (ind+1) + "/" + stack.getSize());
				saved_file = it.next().get();
				it.remove(); // so list doesn't build up anywhere with Callable-s that have been called already.				
				System.gc();
			} catch (InterruptedException e) {
				IJ.error("Interruption exception!");
				e.printStackTrace();
				exe.shutdownNow();
				return;
			} catch (ExecutionException e) {
				IJ.error("Execution exception!");
				e.printStackTrace();
				exe.shutdownNow();
				return;
			}
			
			if( saved_file.booleanValue() == false)
			{
				IJ.log("Error while saving: " +  makeTargetPath(p.outputPath, stack.getSliceLabel(ind)));
				exe.shutdownNow();
				return;
			}
			
		}
		
		// Shut executor service down to allow garbage collection
		exe.shutdown();

		save_job = null;
		
		IJ.log( "Done." );
	}

	static private String makeTargetPath(final String dir, final String name) 
	{
		String filepath = dir + name;
		if (! name.toLowerCase().matches("^.*ti[f]{1,2}$")) 
			filepath += ".tif";
		return filepath;
	}	
	
	private static Callable<Boolean> applyTransformAndSave(
			final ImageProcessor ip_in, 
			final String file_name, 
			final String target_dir, 
			final CoordinateTransform transform,
			final boolean interpolate,
			final int resolution,
			final int width,
			final int height
			) 
	{
		return new Callable<Boolean>() {
			public Boolean call() {
				// Open next image
				// Calculate transform mesh			
				//CoordinateTransformMesh ctmMesh= new CoordinateTransformMesh( transform, 128, imp2.getWidth(), imp2.getHeight());
				final TransformMeshMapping< CoordinateTransformMesh > mapping = new TransformMeshMapping< CoordinateTransformMesh >( new CoordinateTransformMesh( transform, resolution, ip_in.getWidth(), ip_in.getHeight() ) );
									
				// Create interpolated deformed image with black background
				ip_in.setValue(0);
				final ImageProcessor ip_out = ip_in.createProcessor( width,height );
				if ( interpolate )
				{
					mapping.mapInterpolated( ip_in, ip_out );
				}
				else
				{
					mapping.map( ip_in, ip_out );
				}
				
				//final ImageProcessor ip2 = interpolate ? mapping.createMappedImageInterpolated(imp2.getProcessor()) : mapping.createMappedImage(imp2.getProcessor()); 
				
				
				//imp2.show();

				ImagePlus imp_out=new ImagePlus(file_name,ip_out);
				// Save target image
				return new FileSaver(imp_out).saveAsTiff(makeTargetPath(target_dir, file_name));
			}
		};
	}
	
	static private FloatProcessor createMask( final ImageProcessor source )
	{
		final FloatProcessor mask = new FloatProcessor( source.getWidth(), source.getHeight() );
		final int maskColor = 0x0000ff00;
		final int n = source.getWidth() * source.getHeight();
		final float[] maskPixels = ( float[] )mask.getPixels();
		for ( int i = 0; i < n; ++i )
		{
			final int sourcePixel = source.get( i ) & 0x00ffffff;
			if ( sourcePixel == maskColor )
				maskPixels[ i ] = 0;
			else
				maskPixels[ i ] = 1;
		}
		return mask;
	}

	public void keyPressed(KeyEvent e)
	{
		if (
				( e.getKeyCode() == KeyEvent.VK_F1 ) &&
				( e.getSource() instanceof TextField ) )
		{
			
		}
	}

	public void keyReleased(KeyEvent e) { }

	public void keyTyped(KeyEvent e) { }
	
	
	
	final static private class Features implements Serializable
	{
		private static final long serialVersionUID = 2689219384710526198L;
		
		final FloatArray2DSIFT.Param param;
		final ArrayList< Feature > features;
		Features( final FloatArray2DSIFT.Param p, final ArrayList< Feature > features )
		{
			this.param = p;
			this.features = features;
		}
	}
	
	final static private boolean serializeFeatures(
			final FloatArray2DSIFT.Param param,
			final ArrayList< Feature > fs,
			final String path )
	{
		return serialize( new Features( param, fs ), path );
	}

	final static private ArrayList< Feature > deserializeFeatures( final FloatArray2DSIFT.Param param, final String path )
	{
		Object o = deserialize( path );
		if ( null == o ) return null;
		Features fs = (Features) o;
		if ( param.equals( fs.param ) )
			return fs.features;
		return null;
	}
	
	final static private class PointMatches implements Serializable
	{
		private static final long serialVersionUID = -2564147268101223484L;
		
		ElasticAlignForrest.Param param;
		ArrayList< PointMatch > pointMatches;
		PointMatches( final ElasticAlignForrest.Param p, final ArrayList< PointMatch > pointMatches )
		{
			this.param = p;
			this.pointMatches = pointMatches;
		}
	}
	
	final static private boolean serializePointMatches(
			final ElasticAlignForrest.Param param,
			final ArrayList< PointMatch > pms,
			final String path )
	{
		return serialize( new PointMatches( param, pms ), path );
	}
	
	final static private ArrayList< PointMatch > deserializePointMatches( final ElasticAlignForrest.Param param, final String path )
	{
		Object o = deserialize( path );
		if ( null == o ) return null;
		PointMatches pms = (PointMatches) o;
		if ( param.equalSiftPointMatchParams( pms.param ) )
			return pms.pointMatches;
		return null;
	}
	
	/** Serializes the given object into the path. Returns false on failure. */
	static public boolean serialize(final Object ob, final String path) {
		try {
			// 1 - Check that the parent chain of folders exists, and attempt to create it when not:
			File fdir = new File(path).getParentFile();
			if (null == fdir) return false;
			fdir.mkdirs();
			if (!fdir.exists()) {
				IJ.log("Could not create folder " + fdir.getAbsolutePath());
				return false;
			}
			// 2 - Serialize the given object:
			final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path));
			out.writeObject(ob);
			out.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/** Attempts to find a file containing a serialized object. Returns null if no suitable file is found, or an error occurs while deserializing. */
	static public Object deserialize(final String path) {
		try {
			if (!new File(path).exists()) return null;
			final ObjectInputStream in = new ObjectInputStream(new FileInputStream(path));
			final Object ob = in.readObject();
			in.close();
			return ob;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
