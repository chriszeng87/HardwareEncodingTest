package com.android.grafika;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.util.Matrix;

/**
 * Created by kevenwu on 13-7-18.
 */
public class MP4ParserUtil {
    private static final String TAG = "MP4ParserUtil";

    public static void shortenTrack(Track track) {

    }

    public static boolean combineVideoAndAudio(File toFile, File video, File audio) {

        return true;
    }

	public static boolean silenceMovie(String input, String output) {
		try {
			LinkedList<Track> localLinkedList1 = new LinkedList<Track>();
			Movie m = MovieCreator.build(input);
			Iterator<?> itr = m.getTracks().iterator();
			while (itr.hasNext()) {
				Track track = (Track) itr.next();
				if (track.getHandler().equals("vide")) {
					localLinkedList1.add(track);
				}
			}
			// }

			Movie localMovie = new Movie();
			if (localLinkedList1.size() > 0) {
				if (localLinkedList1.size() == 1) {
					localMovie.addTrack((Track) localLinkedList1.getFirst());
				} else {
					localMovie
							.addTrack(new AppendTrack(
									(Track[]) localLinkedList1
											.toArray(new Track[localLinkedList1
													.size()])));
				}
			}
			Container container = new DefaultMp4Builder().build(localMovie);
			@SuppressWarnings("resource")
			FileChannel localFileChannel = new RandomAccessFile(output, "rw")
					.getChannel();
			localFileChannel.position(0L);
			container.writeContainer(localFileChannel);
			localFileChannel.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean replaceMusic(String processedVideo, String musicPath,
			String outPath) {
		FileChannel localFileChannel = null;
		try {
			LinkedList<Track> vedioTrackList = new LinkedList<Track>();
			LinkedList<Track> audioTrackListOrg = new LinkedList<Track>();
			LinkedList<Track> audioTrackList = new LinkedList<Track>();
			{
				Movie video = MovieCreator.build(processedVideo);
				Iterator<?> itr = video.getTracks().iterator();
				while (itr.hasNext()) {
					Track track = (Track) itr.next();
					if (track.getHandler().equals("vide")) {
						vedioTrackList.add(track);
					}
					if (track.getHandler().equals("soun")) {
						audioTrackListOrg.add(track);
					}
				}
			}
			{
				Movie musicMovie = MovieCreator.build(musicPath);
				Iterator<?> itr = musicMovie.getTracks().iterator();
				while (itr.hasNext()) {
					Track track = (Track) itr.next();
					if (track.getHandler().equals("soun")) {
						audioTrackList.add(track);
					}
				}
			}
			Movie localMovie = new Movie();
			if (vedioTrackList.size() > 0) {
				if (vedioTrackList.size() == 1) {
					localMovie.addTrack((Track) vedioTrackList.getFirst());
				} else {
					localMovie
							.addTrack(new AppendTrack((Track[]) vedioTrackList
									.toArray(new Track[vedioTrackList.size()])));
				}
			}
			double time = 8;
			if(audioTrackListOrg.size()>0) {
				Track orgAudio = (Track)audioTrackListOrg.getFirst();
				time = getAudioTime(orgAudio);
//				localMovie.addTrack(orgAudio);
			}
//			long size = getDuration();
			if (audioTrackList.size() > 0) {
				if (audioTrackList.size() == 1) {
					Track audio = audioTrackList.getFirst();
					addTrackByTime(localMovie,audio,time);
				} else {
					
					for(Track audio : audioTrackList) {
						addTrackByTime(localMovie,audio,time);
					}
				}
			}
			Container container = new DefaultMp4Builder().build(localMovie);

			localFileChannel = new RandomAccessFile(outPath, "rw")
					.getChannel();
			localFileChannel.position(0L);
			container.writeContainer(localFileChannel);
			localFileChannel.close();
		} catch (Exception e) {
			e.printStackTrace();
			if(localFileChannel != null){
				try {
					localFileChannel.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			return false;
		}
		return true;

	}
	
	private static double getAudioTime(Track track) {
		double currentTime = 0;
		for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
			TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
			for (int j = 0; j < entry.getCount(); j++) {
				currentTime += (double) entry.getDelta()
						/ (double) track.getTrackMetaData().getTimescale();
			}
		}
		return currentTime;
	}
	
	private static void addTrackByTime(Movie localMovie, Track track,
			double endTime) throws IOException {
		long currentSample = 0;
		double currentTime = 0;
		double lastTime = 0;
		long endSample1 = -1;

		for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
			TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
			for (int j = 0; j < entry.getCount(); j++) {

				if (currentTime > lastTime && currentTime <= endTime) {
					endSample1 = currentSample;
				}
				lastTime = currentTime;
				currentTime += (double) entry.getDelta()
						/ (double) track.getTrackMetaData().getTimescale();
				currentSample++;
			}
		}
		localMovie.addTrack(new AppendTrack(new CroppedTrack(track, 0,
				endSample1)));
	}
	
	public static boolean stitchMovies(String[] input, String output) {
		if (input == null || output == null) {
			return false;
		}
		File[] files = new File[input.length + 1];
		int i = 0;
		for(String name : input) {
			File f = new File(name);
			if (f != null && f.isFile()) {
				files[i] = f;
			}
			i++;
		}
		files[i] = new File(output);
		return stitchMovies(files);
	}

	public static boolean stitchMovies(File[] paramArrayOfFile) {
		int num = paramArrayOfFile.length - 1; // the last one stores the
												// result.
		File output = paramArrayOfFile[num]; // result file.
		LinkedList localLinkedList1 = new LinkedList();
		LinkedList localLinkedList2 = new LinkedList();
		try {
			// FileChannel[] arrayOfFileChannel = new FileChannel[num];
			// for (int j = 0; j < num; ++j) {
			// arrayOfFileChannel[j] = new
			// FileInputStream(paramArrayOfFile[j]).getChannel();
			// }

			List<Movie> list = new ArrayList<Movie>();
			for (int k = 0; k < num; ++k) {
				Movie m = null;
				try {
					// m = MovieCreator.build(arrayOfFileChannel[k]);
					m = MovieCreator.build(paramArrayOfFile[k].getPath());
				} catch (Exception e) {
					continue;
				}
				list.add(m);
			}

            Movie[] arrayOfMovie = list.toArray(new Movie[list.size()]);
            num = list.size();
            if(num == 0) return false;

            for (int p = 0; p < num; p++) {
                Iterator itr = arrayOfMovie[p].getTracks().iterator();
                while (itr.hasNext()) {
                    Track track = (Track)itr.next();
                    if (track.getHandler().equals("soun")) {
                        localLinkedList2.add(track);
                    }
                    else if (track.getHandler().equals("vide")) {
                        localLinkedList1.add(track);
                    }
                }
            }

            Movie localMovie = new Movie();
            if (localLinkedList1.size() > 0) {
                if (localLinkedList1.size() == 1) {
                    localMovie.addTrack((Track)localLinkedList1.getFirst());
                } else {
                    localMovie.addTrack(new AppendTrack((Track[])localLinkedList1.toArray(new Track[localLinkedList1.size()])));
                }
            }
            if (localLinkedList2.size() > 0) {
                if (localLinkedList2.size() == 1) {
                    localMovie.addTrack((Track)localLinkedList2.getFirst());
                } else {
                    localMovie.addTrack(new AppendTrack((Track[])localLinkedList2.toArray(new Track[localLinkedList2.size()])));
                }
            }
            //IsoFile localIsoFile = (IsoFile) new DefaultMp4Builder().build(localMovie);
            //rotateIsoFile(localIsoFile, 90);
            //FileChannel localFileChannel = new RandomAccessFile(output, "rw").getChannel();
            //localFileChannel.position(0L);
            //localIsoFile.getBox(localFileChannel);
            //localFileChannel.close();
            
//            localMovie.setMatrix(Matrix.ROTATE_90);
//            ((Track)localMovie.getTracks().get(0)).getTrackMetaData().setMatrix(Matrix.ROTATE_90);
            Container container = new DefaultMp4Builder().build(localMovie);
            FileChannel localFileChannel = new RandomAccessFile(output, "rw").getChannel();
            localFileChannel.position(0L);
            container.writeContainer(localFileChannel);
            localFileChannel.close();
            //rotateMovie(output.getAbsolutePath(), 90);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

	public static boolean rotateVideo(String filename, String output, int degree) {
		try {
			IsoFile isoFile = new IsoFile(filename);
			degree = (degree + getVideoDegree(isoFile)) % 360;
			rotateIsoFile(isoFile, degree);
			FileChannel fc = new RandomAccessFile(output, "rw").getChannel();
            fc.position(0L);
            isoFile.getBox(fc);
            fc.close();
            isoFile.close();
		} catch (IOException ex1) {
			ex1.printStackTrace();
			return false;
		} catch (Exception ex2) {
			ex2.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean rotateMovie(String filename, String output, int rotate) {
		try {
			// FileChannel fileChannel = new RandomAccessFile(filename,
			// "rw").getChannel();
			Movie movie = MovieCreator.build(filename);
			if (rotate == 90) {
				movie.setMatrix(Matrix.ROTATE_90);
	            ((Track)movie.getTracks().get(0)).getTrackMetaData().setMatrix(Matrix.ROTATE_90);			
			} else if (rotate == 270) {
				movie.setMatrix(Matrix.ROTATE_270);
	            ((Track)movie.getTracks().get(0)).getTrackMetaData().setMatrix(Matrix.ROTATE_270);
			} else if (rotate == 180) {
				movie.setMatrix(Matrix.ROTATE_180);
	            ((Track)movie.getTracks().get(0)).getTrackMetaData().setMatrix(Matrix.ROTATE_180);
			} else {
				return false;
			}
			
            Container container = new DefaultMp4Builder().build(movie);
            FileChannel localFileChannel = new RandomAccessFile(output, "rw").getChannel();
            localFileChannel.position(0L);
            container.writeContainer(localFileChannel);
            localFileChannel.close();
            return true;
            /*
            IsoFile isoFile = new IsoFile(fileChannel);
            rotateIsoFile(isoFile, rotate);
            fileChannel.close();
            FileChannel localFileChannel = new RandomAccessFile(filename, "rw").getChannel();
            localFileChannel.position(0L);
            isoFile.writeContainer(localFileChannel);*/
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
	
	public static int getVideoDegree(IsoFile isoFile) {
		TrackBox tb = isoFile.getMovieBox().getBoxes(TrackBox.class).get(0);
        TrackHeaderBox box = tb.getTrackHeaderBox();
        int degree = 0;
        Matrix m = box.getMatrix();
        if (m == Matrix.ROTATE_90) {
        	degree = 90;
        } else if (m == Matrix.ROTATE_180) {
        	degree = 180;
        } else if (m == Matrix.ROTATE_270) {
        	degree = 270;
        }
        return degree;
	}
	
	public static int getVideoOrientation(String video) {
		IsoFile isoFile = null;
		int orientation = 0;
		try {
			isoFile = new IsoFile(video);
			TrackBox tb = isoFile.getMovieBox().getBoxes(TrackBox.class).get(0);
	        TrackHeaderBox box = tb.getTrackHeaderBox();
	        Matrix m = box.getMatrix();
	        if (m.equals(Matrix.ROTATE_90)) {
	        	orientation = 90;
	        } else if (m.equals(Matrix.ROTATE_180)) {
	        	orientation = 180;
	        } else if (m.equals(Matrix.ROTATE_270)) {
	        	orientation = 270;
	        }
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (isoFile != null) {
				try {
					isoFile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return orientation;
	}

    public static void rotateIsoFile(IsoFile isoFile, int rotate){
        /*
        long[] m = null;
        switch (rotate) {
            case 0:
                m = new long[]{
                        0x00010000, 0, 0,
                        0, 0x00010000, 0,
                        0, 0, 0x40000000
                };
                break;
            case 90:
                m = new long[]{
                        0,0x00010000, 0,
                        -0x00010000, 0, 0,
                        0, 0, 0x40000000
                };
                break;
            case 180:
                m = new long[]{
                        0x00010000, 0, 0,
                        0, 0x00010000, 0,
                        0, 0, 0x40000000
                };
                break;
            case 270:
                m = new long[]{
                        -0x00010000, 0, 0,
                        0, -0x00010000, 0,
                        0, 0, 0x40000000
                };
                break;
            default:
                break;
        }*/
        com.googlecode.mp4parser.util.Matrix m = null;
        switch (rotate) {
            case 0:
                m = Matrix.ROTATE_0;
                break;
            case 90:
                m = Matrix.ROTATE_90;
                break;
            case 180:
                m = Matrix.ROTATE_180;
                break;
            case 270:
                m = Matrix.ROTATE_270;
                break;
            default:
                break;
        }
        //isoFile.getMovieBox().getMovieHeaderBox().setMatrix(m);
        TrackBox tb = isoFile.getMovieBox().getBoxes(TrackBox.class).get(0);
        TrackHeaderBox box = tb.getTrackHeaderBox();
        box.setMatrix(m);
    }

    public static double getDuration(IsoFile isoFile) {
    	try {
    		double lengthInSeconds = (double)
                    isoFile.getMovieBox().getMovieHeaderBox().getDuration() /
                    isoFile.getMovieBox().getMovieHeaderBox().getTimescale();
            return lengthInSeconds;
		} catch (Exception e) {
//			L.e(TAG, e);
			return -1;
		}
    }

    public static double getDuration(String filename) {
        IsoFile isoFile = null;
        double lengthInSeconds = 0;
		try {
			// isoFile = new IsoFile(new FileInputStream(new
			// File(filename)).getChannel());
			isoFile = new IsoFile(filename);
            lengthInSeconds = getDuration(isoFile);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return lengthInSeconds;
    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }
}
