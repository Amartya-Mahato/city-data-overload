
"use client";

import * as React from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { Megaphone } from "lucide-react";
import { Label } from "@/components/ui/label";

export function BroadcastAlertCard() {
  const [message, setMessage] = React.useState("There will be a power cut in your area for 6 hours due to a Power Grid Issue.");
  const { toast } = useToast();

  const handleSendAlert = () => {
    if (message.trim() === "") {
      toast({
        title: "Error",
        description: "Alert message cannot be empty.",
        variant: "destructive",
      });
      return;
    }

    toast({
      title: "Broadcast Sent from Police Department",
      description: message,
      variant: "destructive"
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Megaphone /> Broadcast Alert</CardTitle>
        <CardDescription>Send a notification to all users in a specific area.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <Label htmlFor="broadcast-message">Message</Label>
          <Textarea
            id="broadcast-message"
            placeholder="Type your alert message here..."
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            className="mt-2"
          />
        </div>
        <Button onClick={handleSendAlert} className="w-full">Send Alert to All Users</Button>
      </CardContent>
    </Card>
  );
}
