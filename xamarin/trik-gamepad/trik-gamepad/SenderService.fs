namespace trik.gamepad
open Android.Widget
open Android.Content
open Android.App
open Android.Util
open System.Net

(*package com.trik.gamepad;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;
*)

type SenderService (mainActivity: Activity) = 
    let mutable _stream = None
    let SOS = [| 0L; 50L; 50L; 50L; 50L; 50L; 
                100L; 200L; 50L; 200L; 50L; 200L;
                100L;  50L; 50L; 50L; 50L; 50L|]

    let vibrator : Android.OS.Vibrator = downcast mainActivity.GetSystemService Context.VibratorService
    let onDisconnect = new Event<_>()

    let toast (msg : string) = Toast.MakeText(mainActivity, msg, ToastLength.Long).Show()

    [<CLIEvent>]
    member x.Disconnected = onDisconnect.Publish

    member x.Connect (host:string, port) =
        let connect()=
            Log.Info ("TCP Client", "C: Connecting...") |> ignore
            let socket = new Sockets.TcpClient()
            socket.NoDelay <- true
            socket.SendTimeout <- 5000
            try
                socket.Connect(host, port)
                Some <| new System.IO.StreamWriter(socket.GetStream(), AutoFlush = true)
            with 
                e -> Log.Error("TCP", "Stream: {0}", e.ToString()) |> ignore
                     socket.Close() 
                     None
         
        _stream <- async { return connect()} |> Async.RunSynchronously
        let connected = _stream.IsSome
        toast <| "Connection to " + host + ":" + string port +  if connected then " established." else " error."
        connected       
    
    member this.Disconnect (reason : string) = 
        if _stream.IsSome then
            _stream.Value.Close()
            _stream <- None
            Log.Debug("TCP", "Disconnected.")  |> ignore
            onDisconnect.Trigger(this, reason)
   
    member x.Send (command : string) = 
        Log.Debug("TCP", "Sending '" + command + "'") |> ignore
        match _stream with
          | None -> ()
          | Some stream ->  
            async {
                    try 
                        stream.WriteLine command 
                        vibrator.Vibrate 10L
                    with
                    e ->    Log.Error("TCP", "NotSent: {0}, Touble : {1} ", command, e.Message) |> ignore
                            x.Disconnect "Send failed."
                            vibrator.Vibrate(SOS, -1)
             } |> Async.RunSynchronously

    