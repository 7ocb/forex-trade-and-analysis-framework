int chunk_life_length = 1;  //seconds
string out_folder;
int data_collection_started;
string collected_data;

int init() {
    
   out_folder = Symbol() + "/";
   
   initNewChunk();
   return(0);
}
 
 
int deinit() {
   endThisChunk();
   return(0);
}  
 
 
int start() { //run on each tick
   
   
   string s = StringConcatenate(getCurrentTimeStr(), " ", DoubleToStr(Bid, Digits), "\n");
   collected_data = StringConcatenate(collected_data, s);
 
   if (TimeLocal() - data_collection_started >= chunk_life_length ) {
      endThisChunk();
      initNewChunk();
   }

   return(0);
}


int writeFile(string file_path, string data) {
   int output_file = FileOpen(file_path, FILE_WRITE | FILE_CSV, " ");
   FileWrite(output_file, data);
   FileClose(output_file);
   return(0);
}


string makeFilePath() {
   string file_name = getCurrentTimeStr() + ".ticks";
   string file_path = out_folder + file_name;
   return (file_path);
}



string fixLeadingZero(int t) {

   string tmp = DoubleToStr(t, 0);
   
   if( StringLen(tmp) == 1 ) {
      tmp = StringConcatenate("0", tmp);
      return (tmp);
   }
   else 
   return(tmp); 
}

string getCurrentTimeStr() {
   int current_time = TimeLocal(	);

   string Y = TimeYear(current_time);
   string M = fixLeadingZero(TimeMonth(current_time));
   string D = fixLeadingZero(TimeDay(current_time));
   
   string h = fixLeadingZero(TimeHour(current_time));
   string m = fixLeadingZero(TimeMinute(current_time));
   string s = fixLeadingZero(TimeSeconds(current_time));
   
   string timeString = StringConcatenate(Y,"-",M,"-",D,"-",h,"-",m,"-",s);
   
   return (timeString);
}


int endThisChunk(){
   collected_data = StringConcatenate(collected_data,"EOF");
   writeFile(makeFilePath(), collected_data);
   return (0);
}


int initNewChunk(){
   collected_data = "";
   data_collection_started = TimeLocal();
   return (0);
}

















