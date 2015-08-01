
/**
 * AppleIIGo
 * The Java Apple II Emulator 
 * (C) 2006 by Marc S. Ressl (ressl@lonetree.com)
 * (C) 2008 by Nick Westgate (Nick.Westgate@gmail.com)
 * Released under the GPL
 * 
 * Change list:
 * 
 * Version 1.0.2 - changes by Nick:
 * - improved sound sync by moving AppleSpeaker into the main thread
 * - added version (F1)
 * - added multiple disks & switching (F3, F4)
 * - added ZIP archive support
 * - fixed HTTP disk image access bug
 */

import java.applet.Applet;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * AppleIIGo class<p>
 * Connects EmAppleII, AppleCanvas
 */
public class AppleIIGo extends Applet implements KeyListener, ComponentListener, 
	MouseListener, MouseMotionListener {

	final String version = "1.0.2";
	final String versionString = "AppleIIGo Version " + version;

	// Class instances
	private EmAppleII apple;
	private AppleDisplay display;
	private DiskII disk;

	// Machine variables
	private boolean isCpuPaused;
	private boolean isCpuDebugEnabled;

	// Keyboard variables
	private boolean keyboardUppercaseOnly;

	// Paddle variables
	private boolean isPaddleInverted;
	
	// Disk variables
	private String diskDriveResource[] = new String[2];
	private boolean diskWritable;
	private String[] disks0;
	private String[] disks1;
	private int disk0;
	private int disk1;

	/**
 	 * Debug
	 */
	private void debug(String message) {
//		System.out.println(message);
	}

	/**
 	 * Parameters
	 */
	private String getAppletParameter(String parameter, String defaultValue) {
		String value = getParameter(parameter);
		if ((value == null) || (value.length() == 0))
			return defaultValue;
		return value;
	}

	/**
 	 * On applet initialization
	 */
	public void init() {
		debug("init()");

		// Activate listeners
		addKeyListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);

		if (getAppletParameter("displayFocusOnStart", "true").equals("true"))
			addComponentListener(this);

		// Initialize Apple II emulator
		apple = new EmAppleII();
		loadRom(getAppletParameter("cpuRom", ""));
		apple.setCpuSpeed(new Integer(getAppletParameter("cpuSpeed", "1000")).intValue());
		isCpuPaused = getAppletParameter("cpuPaused", "false").equals("true");
		isCpuDebugEnabled = getAppletParameter("cpuDebugEnabled", "false").equals("true");
		apple.setStepMode(getAppletParameter("cpuStepMode", "false").equals("true"));
	
		// Keyboard
		keyboardUppercaseOnly = getAppletParameter("keyboardUppercaseOnly", "true").equals("true");

		// Display
		display = new AppleDisplay(this, apple);
		display.setScale(new Float(getAppletParameter("displayScale", "1")).floatValue());
		display.setRefreshRate(new Integer(getAppletParameter("displayRefreshRate", "10")).intValue());
		display.setColorMode(new Integer(getAppletParameter("displayColorMode", "0")).intValue());
		display.setStatMode(getAppletParameter("displayStatMode", "false").equals("true"));
		display.setGlare(getAppletParameter("displayGlare", "false").equals("true"));

		// Speaker
		apple.speaker = new AppleSpeaker(apple);
		apple.speaker.setVolume(new Integer(getAppletParameter("speakerVolume", "6")).intValue());
		
		// Peripherals
		disk = new DiskII();
		apple.setPeripheral(disk, 6);

		// Initialize disk drives
		diskWritable = getAppletParameter("diskWritable", "false").equals("true");
		disks0 = getAppletParameter("diskDrive1", "").split("[|]");
		disk0 = 0;
		disks1 = getAppletParameter("diskDrive2", "").split("[|]");
		disk1 = 0;
		mountDisk(0, disks0[disk0]);
		mountDisk(1, disks1[disk1]);

		// Start CPU
		if (!isCpuPaused)
			resume();
	}

	/**
 	 * Start applet
	 */
	public void start() {
		debug("AppleIIGo Version " + version);
		debug("start()");
	}

	/**
 	 * Stop applet
	 */
	public void stop() {
		debug("stop()");
	}

	/**
 	 * On applet destruction
	 */
	public void destroy() {
		debug("destroy()");
		unmountDisk(0);
		unmountDisk(1);
	}
	


	// Public Java interface

	/**
 	 * Javascript interface
	 */
	public void focus() {
		debug("focus()");
		requestFocus();
	}

	/**
 	 * Pause emulator
	 */
	public void pause() {
		debug("pause()");
		isCpuPaused = true;
		apple.setPaused(isCpuPaused);
		display.setPaused(isCpuPaused);
		apple.speaker.setPaused(isCpuPaused);
	}

	/**
 	 * Resume emulator
	 */
	public void resume() {
		debug("resume()");
		isCpuPaused = false;
		apple.speaker.setPaused(isCpuPaused);
		display.setPaused(isCpuPaused);
		apple.setPaused(isCpuPaused);
	}

	/**
 	 * Restarts emulator
	 */
	public void restart() {
		debug("restart()");
		apple.restart();
	}
	
	/**
	 * Open input stream
	 */
	private DataInputStream openInputStream(String resource) {
		InputStream is = null;
		
		try {
			URL url = new URL(getCodeBase(), resource);
			debug("resource: " + url.toString());

			is = url.openStream();
			if (resource.toLowerCase().endsWith(".gz"))
			{
				is = new GZIPInputStream(is);
			}
			else if (resource.toLowerCase().endsWith(".zip"))
			{
				is = new ZipInputStream(is);
				((ZipInputStream)is).getNextEntry();
			}
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		if (is == null)
		{
			debug("failed");
			return null;
		}
		else
		{
			debug("ok");
			return new DataInputStream(is);
		}
	}

	/**
	 * Open output stream
	 */
	private OutputStream openOutputStream(String resource) {
		OutputStream os = null;
		
		try {
			if (!(resource.substring(0, 6).equals("http://")))
				os = new FileOutputStream(resource);
		} catch (Exception e) {
		}
		
		return os;
	}

	/**
	 * Load ROM
	 */
	public boolean loadRom(String resource) {
		debug("loadRom(resource: " + resource + ")");
		boolean success = false;
		
		try {
			DataInputStream is = openInputStream(resource);
			success = apple.loadRom(is);
			is.close();
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		return success;
	}
	
	/**
 	 * Mount a disk
	 */
	public boolean mountDisk(int drive, String resource) {
		debug("mountDisk(drive: " + drive + ", resource: " + resource + ")");
		boolean success = false;

		if ((drive < 0) || (drive > 2))
			return success;
			
		try {
			unmountDisk(drive);

			diskDriveResource[drive] = resource;

			DataInputStream is = openInputStream(resource);
			success = disk.readDisk(drive, is, 254, false);
			is.close();
		} catch (Exception e) {
			debug("Exeption: " + e.getLocalizedMessage());
		}
		
		return success;
	}

	/**
 	 * Unmount a disk
	 */
	public void unmountDisk(int drive) {
		debug("unmountDisk(drive: " + drive + ")");
		if ((drive < 0) || (drive > 2))
			return;

		if (!diskWritable)
			return;
			
		try {
			OutputStream os = openOutputStream(diskDriveResource[drive]);
			disk.writeDisk(drive, os);
			os.close();
		} catch (Exception e) {
		}
	}

	/**
 	 * Set color mode
	 */
	public void setColorMode(int value) {
		debug("setColorMode(value: " + value + ")");
		display.setColorMode(value);
	}

	/**
 	 * Get disk activity
	 */
	public boolean getDiskActivity() {
		return (!isCpuPaused && disk.isMotorOn());
	}
	
	

	/**
 	 * KeyListener event handling
	 */	
	public void keyTyped(KeyEvent e) {
		// Send to emulator
		int key = e.getKeyChar();
		if (key == 10)
			apple.setKeyLatch(13);
		else if (key < 128) {
			if (keyboardUppercaseOnly && (key >= 97) && (key <= 122))
				key -= 32;
			
			apple.setKeyLatch(key);
		}
	}

    public void keyPressed(KeyEvent e) {
		switch(e.getKeyCode()) {
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_LEFT:
			apple.setKeyLatch(8);
			break;
		case KeyEvent.VK_RIGHT:
			apple.setKeyLatch(21);
			break;
		case KeyEvent.VK_UP:
			if (e.isControlDown())
			    apple.speaker.setVolume(apple.speaker.getVolume() + 1);
			else
				apple.setKeyLatch(11);
			break;
		case KeyEvent.VK_DOWN:
			if (e.isControlDown())
			    apple.speaker.setVolume(apple.speaker.getVolume() - 1);
			else
				apple.setKeyLatch(10);
			break;
		case KeyEvent.VK_ESCAPE:
			apple.setKeyLatch(27);
			break;
		case KeyEvent.VK_DELETE:
			apple.setKeyLatch(127);
			break;
		case KeyEvent.VK_HOME:
			if (e.isControlDown())
				apple.restart();
			else
				apple.reset();
			break;
		case KeyEvent.VK_F1:
			showStatus("AppleIIGo Version " + version);
			break;
		case KeyEvent.VK_F3:
			if (disks0.length > 1) {
				disk0 = ++disk0 % disks0.length;
				mountDisk(0, disks0[disk0]);
				showStatus("Disk 1: " + disks0[disk0]);
			}
			break;
		case KeyEvent.VK_F4:
			if (disks1.length > 1) {
				disk1 = ++disk1 % disks1.length;
				mountDisk(1, disks1[disk1]);
				showStatus("Disk 2: " + disks1[disk1]);
			}
			break;
		case KeyEvent.VK_F5:
			if (isCpuDebugEnabled)
				display.setStatMode(!display.getStatMode());
			break;
		case KeyEvent.VK_F6:
			if (isCpuDebugEnabled)
				apple.setStepMode(!apple.getStepMode());
			break;
		case KeyEvent.VK_F7:
			if (isCpuDebugEnabled) {
				apple.setStepMode(apple.getStepMode());
				apple.stepInstructions(1);
			}
			break;
		case KeyEvent.VK_F8:
			if (isCpuDebugEnabled) {
				apple.setStepMode(apple.getStepMode());
				apple.stepInstructions(128);
			}
			break;
		}
    }

    public void keyReleased(KeyEvent e) {
    }
	
	/**
 	 * ComponentListener event handling
	 */	
	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
	}

	public void componentResized(ComponentEvent e) {
	}

	public void componentShown(ComponentEvent e) {
		debug("componentShown()");

		removeComponentListener(this);
		requestFocus();
	}

	/**
 	 * MouseListener, MouseMotionListener event handling
	 */	
	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
		try {
			getAppletContext().showDocument(new URL("javascript:flipMouseOver();"));
		} catch (MalformedURLException ex) {
		}
	}

	public void mouseExited(MouseEvent e) {
		try {
			getAppletContext().showDocument(new URL("javascript:flipMouseOut();"));
		} catch (MalformedURLException ex) {
		}
	}

	public void mousePressed(MouseEvent e) {
		int modifiers = e.getModifiers();
		
		if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
			apple.paddle.setButton(0, true);
		if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
			apple.paddle.setButton(1, true);
	}

	public void mouseReleased(MouseEvent e) {
		int modifiers = e.getModifiers();
		
		if ((modifiers & InputEvent.BUTTON1_MASK) != 0)
			apple.paddle.setButton(0, false);
		if ((modifiers & InputEvent.BUTTON3_MASK) != 0)
			apple.paddle.setButton(1, false);
	}

	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}

	public void mouseMoved(MouseEvent e) {
		if (isPaddleInverted) {
			apple.paddle.setPaddlePos(0, (int) (display.getScale() * (255 - e.getY() * 256 / 192)));
			apple.paddle.setPaddlePos(1, (int) (display.getScale() * (255 - e.getX() * 256 / 280)));
		} else {
			apple.paddle.setPaddlePos(0, (int) (e.getX() * display.getScale() * 256 / 280));
			apple.paddle.setPaddlePos(1, (int) (e.getY() * display.getScale() * 256 / 192));
		}
	}

	/**
 	 * Applet paint function
	 */
	public void paint(Graphics g) {
		display.paint(g);
	}

	/**
 	 * Applet update function
	 */
	public void update(Graphics g) {
		display.paint(g);
	}	
}