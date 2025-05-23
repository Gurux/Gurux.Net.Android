See An [Gurux](http://www.gurux.org/ "Gurux") for an overview.

With gurux.net.android component you can send data easily syncronously or asyncronously using TCP/IP or UDP in Android devices.

Join the Gurux Community or follow [@Gurux](https://twitter.com/guruxorg "@Gurux") for project updates.

Open Source GXNet media component, made by Gurux Ltd, is a part of GXMedias set of media components, which programming interfaces help you implement communication by chosen connection type. Our media components also support the following connection types: serial port.

For more info check out [Gurux](http://www.gurux.org/ "Gurux").

With gurux.net component you can send data easily syncronously or asyncronously.

We are updating documentation on Gurux web page. 

If you have problems you can ask your questions in Gurux [Forum](http://www.gurux.org/forum).

You can get source codes from http://www.github.com/gurux or if you use gradle add this to your build.gradle-file:

```java
compile 'org.gurux:gurux.net.android:1.0.1'
```

Simple example
=========================== 
Before use you must set following settings:
* HostName
* Port
* Protocol

It is also good to add listener and start to listen following events.
* onError
* onReceived
* onMediaStateChange
* onTrace
* onPropertyChanged

```java

GXNet cl = new GXNet(this);
cl.setHostName("www.gurux.fi");
cl.setPort(1000);
cl.setProtocol(NetworkType.TCP);
cl.open();

```

Data is send with send command:

```java
cl.Send("Hello World!");
```
In default mode received data is coming as asynchronously from onReceived event.

Event listener is adding class that you want to use to listen media events and derive class from IGXMediaListener.

```java
*/
 Media listener.
*/
public class MainActivity extends AppCompatActivity implements IGXMediaListener {
{
	/** 
    Represents the method that will handle the error event of a Gurux component.

    @param sender The source of the event.
    @param ex An Exception object that contains the event data.
    */
    @Override
    void onError(Object sender, RuntimeException ex)
    {
	//Show occured error.
    }

    /** 
    Media component sends received data through this method.

    @param sender The source of the event.
    @param e Event arguments.
    */
    @Override
    void onReceived(Object sender, ReceiveEventArgs e)
    {
	//Handle received asyncronous data here.
    }

    /** 
    Media component sends notification, when its state changes.

    @param sender The source of the event.    
    @param e Event arguments.
    */
    @Override
    void onMediaStateChange(Object sender, MediaStateEventArgs e)
    {
	//Media is open or closed.
    }

    /** 
    Called when the Media is sending or receiving data.

    @param sender
    @param e
    @see IGXMedia.Trace Traceseealso>
    */
    @Override
    void onTrace(Object sender, TraceEventArgs e)
    {
	//Send and received data is shown here.
    }
    
    /** 
    Represents the method that will handle the System.ComponentModel.INotifyPropertyChanged.PropertyChanged
    event raised when a property is changed on a component.
    
    @param sender The source of the event.
    @param sender e A System.ComponentModel.PropertyChangedEventArgs that contains the event data.
    */
    @Override
    void onPropertyChanged(Object sender, PropertyChangedEventArgs e)
    {
	//Example port name is change.
    }      
}

```

Listener is registered calling addListener method.
```java
cl.addListener(this);

```

Data can be also send as syncronous if needed.

```java
synchronized (cl.getSynchronous())
{
    String reply = "";    
    ReceiveParameters<byte[]> p = new ReceiveParameters<byte[]>(byte[].class);    
    //End of Packet.
    p.setEop('\n'); 
    //How long reply is waited.   
    p.setWaitTime(1000);          
    cl.send("Hello World!", null);
    if (!cl.receive(p))
    {
        throw new RuntimeException("Failed to receive response..");
    }
}
```
