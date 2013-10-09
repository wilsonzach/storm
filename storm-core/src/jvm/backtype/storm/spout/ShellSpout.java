package backtype.storm.spout;

import backtype.storm.generated.ShellComponent;
import backtype.storm.multilang.ShellMsg;
import backtype.storm.multilang.SpoutMsg;
import backtype.storm.task.TopologyContext;
import backtype.storm.utils.ShellProcess;
import java.util.Map;
import java.util.List;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ShellSpout implements ISpout {
    public static Logger LOG = LoggerFactory.getLogger(ShellSpout.class);

    private SpoutOutputCollector _collector;
    private String[] _command;
    private ShellProcess _process;
    private SpoutMsg spoutMsg;

    public ShellSpout(ShellComponent component) {
        this(component.get_execution_command(), component.get_script());
    }

    public ShellSpout(String... command) {
        _command = command;
    }

    public void open(Map stormConf, TopologyContext context,
                     SpoutOutputCollector collector) {
        _collector = collector;
        _process = new ShellProcess(_command);

        Number subpid = _process.launch(stormConf, context);
        LOG.info("Launched subprocess with pid " + subpid);
    }

    public void close() {
        _process.destroy();
    }

    public void nextTuple() {
        if (spoutMsg == null) {
            spoutMsg = new SpoutMsg();
        }
        spoutMsg.setCommand("next");
        spoutMsg.setId("");
        querySubprocess();
    }

    public void ack(Object msgId) {
    	if (spoutMsg == null) {
            spoutMsg = new SpoutMsg();
        }
        spoutMsg.setCommand("ack");
        spoutMsg.setId(msgId.toString());
        querySubprocess();
    }

    public void fail(Object msgId) {
    	if (spoutMsg == null) {
            spoutMsg = new SpoutMsg();
        }
        spoutMsg.setCommand("fail");
        spoutMsg.setId(msgId.toString());
        querySubprocess();
    }

    private void querySubprocess() {
        try {
            _process.writeSpoutMsg(spoutMsg);

            while (true) {
                ShellMsg shellMsg = _process.readShellMsg();
                String command = shellMsg.getCommand();
                if (command.equals("sync")) {
                    return;
                } else if (command.equals("log")) {
                    String msg = shellMsg.getMsg();
                    LOG.info("Shell msg: " + msg);
                } else if (command.equals("emit")) {
                    String stream = shellMsg.getStream();
                    Long task = shellMsg.getTask();
                    List<Object> tuple = shellMsg.getTuple();
                    Object messageId = shellMsg.getId();
                    if (task == 0) {
                        List<Integer> outtasks = _collector.emit(stream, tuple, messageId);
                        if (shellMsg.areTaskIdsNeeded()) {
                            _process.writeTaskIds(outtasks);
                        }
                    } else {
                        _collector.emitDirect((int) task.longValue(), stream,
                                tuple, messageId);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }
}
