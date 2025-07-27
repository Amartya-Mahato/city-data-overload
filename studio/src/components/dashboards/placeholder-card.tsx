import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
import Image from "next/image";

type Props = {
  title: string;
  description: string;
  icon?: React.ReactNode;
  imageUrl?: string;
  imageHint?: string;
};

export function PlaceholderCard({ title, description, icon, imageUrl, imageHint }: Props) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {icon}
          {title}
        </CardTitle>
        {description && <CardDescription>{description}</CardDescription>}
      </CardHeader>
      {imageUrl && (
        <CardContent>
          <div className="aspect-video relative w-full overflow-hidden rounded-md bg-muted">
            <Image 
              src={imageUrl}
              alt={title}
              fill
              className="object-cover"
              data-ai-hint={imageHint}
            />
          </div>
        </CardContent>
      )}
    </Card>
  );
}
