package ch.ethz.inf.vs.android.g54.a3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class ChatManager {
	// Singleton
	private static ChatManager instance;
	public static ChatManager getInstance() {
		if (instance == null) {
			instance = new ChatManager();
		}
		return instance;
	}

	final String SERVER = "vswot.inf.ethz.ch";
	final int MESSAGE_BUFFER_SIZE = 2048;

	String user = "Llama";
	DatagramSocket sockCmd = null;
	ChatThread chatThread = null;
	Handler handler = null;

	String clockIdx;
	Map<String, Integer> clocks = new HashMap<String, Integer>();
	Map<String, String> clients = new HashMap<String, String>();

	private ChatManager() {
		try {
			InetAddress adrServer = InetAddress.getByName(SERVER);
			sockCmd = new DatagramSocket(4000);
			sockCmd.setSoTimeout(10 * 1000); // read timeout of 10s
			sockCmd.connect(adrServer, 4000); // set default socket for send/recv

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	class ChatThread extends Thread {
		boolean initShutdown = false;
		DatagramSocket sockMsg = null;
		Handler handler;

		public ChatThread(Handler handler) {
			this.handler = handler;
		}

		@Override
		public void run() {
			initShutdown = false;
			try {
				sockMsg = new DatagramSocket(4001);
				sockMsg.setSoTimeout(1000);

				while (!initShutdown) {
					try {
						byte[] msg = new byte[MESSAGE_BUFFER_SIZE];
						DatagramPacket pkt = new DatagramPacket(msg, msg.length);
						sockMsg.receive(pkt); // blocking read
						Message m = handler.obtainMessage();
						Bundle b = new Bundle();
						b.putString("msg", new String(pkt.getData()));
						m.setData(b);
						m.sendToTarget();
					} catch (InterruptedIOException e) {
						// receive hit the timeout
					}
				}
				sockMsg.close();

			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void initShutdown() {
			initShutdown = true;
		}
	}
	
	class MessageHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			try {
				JSONObject o = new JSONObject(msg.getData().getString("msg"));
				// TODO: what to do on message ?
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private String cmdReg(String user) {
		JSONObject o = new JSONObject();
		try {
			o.put("cmd", "register");
			o.put("user", user);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return o.toString();
	}

	private String cmdDereg() {
		JSONObject o = new JSONObject();
		try {
			o.put("cmd", "deregister");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return o.toString();
	}

	private String cmdMsg(String text, Map<String, Integer> clocks) {
		JSONObject o = new JSONObject();
		incClockTick();
		JSONObject t = new JSONObject(clocks);
		try {
			o.put("cmd", "message");
			o.put("text", text);
			o.put("time_vector", t);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return o.toString();
	}

	private String cmdGetClients() {
		JSONObject o = new JSONObject();
		try {
			o.put("cmd", "get_clients");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return o.toString();

	}
	
	private JSONObject execCmd(String cmd) {
		try {
			byte[] req = cmd.getBytes();
			DatagramPacket pkt = new DatagramPacket(req, req.length);
			sockCmd.send(pkt);

			byte[] ans = new byte[MESSAGE_BUFFER_SIZE];
			pkt = new DatagramPacket(ans, ans.length);
			sockCmd.receive(pkt); // blocking read
			return new JSONObject(new String(pkt.getData()));
			//return new JSONObject(String.valueOf(ans);

		} catch (JSONException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void sendMsg(String text) {
		execCmd(cmdMsg(text, clocks));
	}

	private void incClockTick() {
		clocks.put(clockIdx, clocks.get(clockIdx) + 1);
	}

	@SuppressWarnings("unchecked")
	public void connect() {
		try {
			// register
			JSONObject o = execCmd(cmdReg(user));
			// answer:
			// {"index":3,"time_vector":{"3":0,"2":70,"1":71,"0":74},"success":"reg_ok"}
			assert (o.get("success").equals("reg_ok"));

			// get clocks (from register answer)
			clockIdx = Integer.toString(o.getInt("index"));
			JSONObject v = o.getJSONObject("time_vector");
			Iterator<String> i = v.keys();
			while (i.hasNext()) {
				// Redundant but will trigger a NumberFormatException if not a number
				Integer c = Integer.decode(i.next());
				clocks.put(c.toString(), v.getInt(c.toString()));
			}

			// get client list
			o = execCmd(cmdGetClients()).getJSONObject("clients");
			// answer: {"clients":
			// {"/129.132.75.130":"QuestionBot","/129.132.252.221":"AnswerBot","/77.58.228.17":"willi"}
			// }
			Iterator<String> s = o.keys();
			while (s.hasNext()) {
				String c = s.next();
				clients.put(c, (String) o.getString(c));
			}

			chatThread = new ChatThread(handler);
			chatThread.start();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (chatThread.isAlive()) {
			chatThread.initShutdown();
			try {
				chatThread.join();

				JSONObject o = execCmd(cmdDereg()); // answer {"success":"dreg_ok"}
				assert (o.getString("success").equals("dreg_ok"));
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	public void setUser(String user) {
		this.user = user;
	}
}
