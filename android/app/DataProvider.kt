object DataProvider {
    val genderData:MuatbleMap<String,Any> = mutableMapOf()
    fun saveData(key:String,value:Any){
    genderData[key] = value
    }
    fun getData(key: String):Any?{
        return genderData[key]
    }
}