
"use client";

import { useEffect, useRef } from 'react';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  CardFooter
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Bell, ArrowRight } from "lucide-react";
import { Button } from '../ui/button';
import Link from 'next/link';

type Alert = {
  time: string;
  type: string;
  urgency: "Low" | "Medium" | "High" | "Critical";
  status: string;
  details: string;
};

type AlertFeedProps = {
  alerts: Alert[];
  title?: string;
  description?: string;
  viewAllLink?: string;
};

const urgencyVariant: Record<Alert['urgency'], 'default' | 'secondary' | 'destructive'> = {
    "Low": "secondary",
    "Medium": "default",
    "High": "destructive",
    "Critical": "destructive",
}

export function AlertFeed({ alerts, title = "Live Alert Feed", description="A chronological list of incoming alerts.", viewAllLink }: AlertFeedProps) {

  return (
    <Card className="h-full flex flex-col">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Bell />{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="flex-grow p-0">
        <ScrollArea className="h-[380px]">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Alert</TableHead>
                <TableHead className="text-right">Urgency</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {alerts.map((alert, index) => (
                <TableRow key={index}>
                  <TableCell>
                    <div className="font-medium">{alert.type}</div>
                    <div className="hidden text-sm text-muted-foreground md:inline">
                      {alert.details} - {alert.time}
                    </div>

                  </TableCell>
                  <TableCell className="text-right">
                    <Badge variant={urgencyVariant[alert.urgency]}>{alert.urgency}</Badge>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </ScrollArea>
      </CardContent>
      {viewAllLink && (
         <CardFooter>
            <Button asChild className="w-full">
                <Link href={viewAllLink}>
                    View All Alerts <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
            </Button>
        </CardFooter>
      )}
    </Card>
  );
}
